package com.aibos.identity.service;

import com.aibos.identity.dto.request.LoginRequest;
import com.aibos.identity.dto.request.RegisterRequest;
import com.aibos.identity.dto.response.RegisterResponse;
import com.aibos.identity.dto.response.TokenResponse;
import com.aibos.identity.dto.response.UserResponse;
import com.aibos.identity.entity.User;
import com.aibos.identity.enums.Tier;
import com.aibos.identity.exception.custom.EmailAlreadyExistsException;
import com.aibos.identity.exception.custom.EmailNotVerifiedException;
import com.aibos.identity.exception.custom.InvalidCredentialsException;
import com.aibos.identity.exception.custom.InvalidVerifyTokenException;
import com.aibos.identity.repository.UserRepository;
import com.aibos.identity.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/**
 * Core user domain service.
 *
 * Registration flow:
 * 1. Validate email uniqueness
 * 2. Hash password (BCrypt cost 12)
 * 3. Auto-assign FREE tier
 * 4. Generate email verification token
 * 5. Persist user
 * 6. Publish EmailVerificationEvent (async, handled by notification service)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    // ── Registration ─────────────────────────────────────────────────────────

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();

        if (userRepository.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        String verifyToken = generateVerifyToken();

        User user = User.builder()
                .email(normalizedEmail)
                .name(request.name().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .tier(Tier.FREE)
                .tierStartDate(LocalDate.now())   // FREE users get tierStartDate on registration
                .tierExpiresAt(null)              // FREE never expires
                .emailVerified(false)
                .verifyToken(verifyToken)
                .verifyTokenExpiresAt(Instant.now().plusSeconds(86400)) // 24h
                .build();

        user = userRepository.save(user);

        // TODO: publish EmailVerificationRequestedEvent for async email dispatch
        log.info("User registered: {} (id={}), verification token issued", normalizedEmail, user.getId());

        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getTier(),
                user.isEmailVerified(),
                "Registration successful. Please check your email to verify your account."
        );
    }

    // ── Email verification ────────────────────────────────────────────────────
//uncomment below once email verification activated
//    @Transactional
//    public void verifyEmail(String token) {
//        String hash = sha256(token);
//        User user = userRepository
//                .findAll()
//                .stream()
//                .filter(u -> hash.equals(u.getVerifyToken())
//                        && u.getVerifyTokenExpiresAt() != null
//                        && Instant.now().isBefore(u.getVerifyTokenExpiresAt()))
//                .findFirst()
//                .orElseThrow(InvalidVerifyTokenException::new);
//
//        user.setEmailVerified(true);
//        user.setVerifyToken(null);
//        user.setVerifyTokenExpiresAt(null);
//        userRepository.save(user);
//        log.info("Email verified for user {}", user.getId());
//    }

    @Transactional
    public void verifyEmail(String token) {

        User user = userRepository.findAll()
                .stream()
                .filter(u -> token.equals(u.getVerifyToken())
                        && u.getVerifyTokenExpiresAt() != null
                        && Instant.now().isBefore(u.getVerifyTokenExpiresAt()))
                .findFirst()
                .orElseThrow(InvalidVerifyTokenException::new);

        user.setEmailVerified(true);
        user.setVerifyToken(null);
        user.setVerifyTokenExpiresAt(null);

        userRepository.save(user);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public TokenResponse login(LoginRequest request, String deviceHint, String ipAddress) {
        User user = userRepository
                .findByEmailAndDeletedAtIsNull(request.email().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issueRefreshToken(user, deviceHint, ipAddress);

        return TokenResponse.of(accessToken, refreshToken);
    }

    // ── Token Refresh ─────────────────────────────────────────────────────────

    @Transactional
    public TokenResponse refresh(String plainRefreshToken, String deviceHint, String ipAddress) {
        // 1. Validate, revoke old token, get user
        User user = refreshTokenService.rotateAndGetUser(plainRefreshToken);

        // 2. Check grace period / auto-downgrade
        if (user.isTierExpired() && !user.isInGracePeriod(3)) {
            user.downgradeToFree();
            userRepository.save(user);
            log.info("Auto-downgraded user {} to FREE during refresh", user.getId());
        }

        // 3. Generate new tokens (DB is authoritative; fresh JWT reflects current tier)
        String accessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = refreshTokenService.issueRefreshToken(user, deviceHint, ipAddress);

        return TokenResponse.of(accessToken, newRefreshToken);
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        User user = findOrThrow(userId);
        return toResponse(user);
    }

    @Transactional
    public void softDelete(UUID userId) {
        int updated = userRepository.softDelete(userId);
        if (updated == 0) throw new IllegalArgumentException("User not found: " + userId);
        refreshTokenService.revokeAllSessions(userId);
        log.info("User soft-deleted: {}", userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public User findOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getName(),
                u.getTier(), u.getTierExpiresAt(), u.isEmailVerified(), u.getOauthProvider());
    }

    private String generateVerifyToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}


