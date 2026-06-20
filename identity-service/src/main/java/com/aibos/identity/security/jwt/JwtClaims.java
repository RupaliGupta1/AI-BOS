package com.aibos.identity.security.jwt;

import com.aibos.identity.enums.Tier;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Typed representation of our JWT claims.
 * <pre>
 * {
 *   "sub": "user-uuid",
 *   "tier": "PRO",
 *   "tier_valid_until": "2026-07-11",
 *   "jti": "uuid",
 *   "iat": ...,
 *   "exp": ...
 * }
 * </pre>
 */
public record JwtClaims(
        UUID userId,
        Tier tier,
        LocalDate tierValidUntil,   // null for FREE
        String jti                  // JWT ID for blocklist revocation
) {}
