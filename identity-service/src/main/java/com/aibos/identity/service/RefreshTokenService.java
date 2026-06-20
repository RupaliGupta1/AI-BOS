package com.aibos.identity.service;

import com.aibos.identity.entity.RefreshToken;
import com.aibos.identity.entity.User;
import com.aibos.identity.exception.custom.InvalidRefreshTokenException;
import com.aibos.identity.repository.RefreshTokenRepository;
import com.aibos.identity.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Manages refresh token lifecycle.
 *
 * Security model:
 * - A cryptographically random 256-bit token is generated each time.
 * - Only SHA-256(token) is stored; the plain value is returned once.
 * - On every refresh, the old token is atomically revoked and a new one issued.
 * - Reuse of a revoked token (replay attack) triggers global session revocation.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int GRACE_PERIOD_DAYS = 3;

    private final RefreshTokenRepository tokenRepository;
    private final JwtProperties jwtProperties;

    /**
     * Issue a new refresh token for the user.
     * Returns the plain token (must be sent to client, never stored).
     */
    @Transactional
    public String issueRefreshToken(User user, String deviceHint, String ipAddress) {
        String plainToken = generateSecureToken();
        String hash = sha256(plainToken);

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hash)
                .deviceHint(deviceHint)
                .ipAddress(ipAddress)
                .expiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()))
                .build();

        tokenRepository.save(token);
        return plainToken;
    }

    /**
     * Validates, revokes current token, and returns the owning User.
     * Caller is responsible for issuing a new token and JWT.
     *
     * @throws InvalidRefreshTokenException if token is invalid, revoked, or expired.
     */
    @Transactional
    public User rotateAndGetUser(String plainToken) {
        String hash = sha256(plainToken);

        RefreshToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!token.isValid()) {
            // Replay attack: revoked token reused. Revoke ALL sessions for safety.
            if (token.isRevoked()) {
                tokenRepository.revokeAllForUser(token.getUser().getId(), Instant.now());
            }
            throw new InvalidRefreshTokenException();
        }

        // Rotate: revoke the consumed token
        token.revoke();
        tokenRepository.save(token);

        return token.getUser();
    }

    /** Revoke all sessions for a user (logout-all). */
    @Transactional
    public void revokeAllSessions(UUID userId) {
        tokenRepository.revokeAllForUser(userId, Instant.now());
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String generateSecureToken() {
        byte[] bytes = new byte[32]; // 256-bit
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

