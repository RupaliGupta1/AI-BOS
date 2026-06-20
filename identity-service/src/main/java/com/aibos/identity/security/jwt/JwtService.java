package com.aibos.identity.security.jwt;

import com.aibos.identity.entity.User;
import com.aibos.identity.enums.Tier;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

/**
 * Stateless JWT access token service.
 *
 * Design decisions:
 * - Short 15-min TTL minimizes window of use after logout/downgrade.
 * - JTI stored in Redis blocklist to support immediate revocation on logout.
 * - tier + tier_valid_until in payload = no DB call on most requests.
 * - DB is authoritative; JWT is only a cached snapshot, refreshed every 15 min.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_TIER = "tier";
    private static final String CLAIM_TIER_VALID_UNTIL = "tier_valid_until";
    private static final String BLOCKLIST_PREFIX = "jwt:blocklist:";

    private final JwtProperties props;
    private final StringRedisTemplate redis;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** Generate a signed access JWT for the given user. */
    public String generateAccessToken(User user) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant exp = now.plus(props.accessTokenTtl());

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_TIER, user.getTier().name())
                .claim(CLAIM_TIER_VALID_UNTIL,
                        user.getTierExpiresAt() != null ? user.getTierExpiresAt().toString() : null)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    /** Parse and validate a JWT, throwing on any error. */
    public JwtClaims parseAndValidate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String jti = claims.getId();

        // Redis blocklist check (revoked on logout)
        if (Boolean.TRUE.equals(redis.hasKey(BLOCKLIST_PREFIX + jti))) {
            throw new JwtException("Token has been revoked");
        }

        String tierValidUntilStr = claims.get(CLAIM_TIER_VALID_UNTIL, String.class);
        LocalDate tierValidUntil = tierValidUntilStr != null
                ? LocalDate.parse(tierValidUntilStr)
                : null;

        return new JwtClaims(
                UUID.fromString(claims.getSubject()),
                Tier.valueOf(claims.get(CLAIM_TIER, String.class)),
                tierValidUntil,
                jti
        );
    }

    /**
     * Add a JTI to the Redis blocklist.
     * TTL is set to match the token's remaining lifetime so Redis auto-cleans it.
     */
    public void blacklistToken(String jti, Instant tokenExp) {
        long ttlSeconds = tokenExp.getEpochSecond() - Instant.now().getEpochSecond();
        if (ttlSeconds > 0) {
            redis.opsForValue().set(
                    BLOCKLIST_PREFIX + jti,
                    "1",
                    java.time.Duration.ofSeconds(ttlSeconds)
            );
        }
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(BLOCKLIST_PREFIX + jti));
    }
}


