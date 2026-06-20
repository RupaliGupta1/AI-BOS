package com.aibos.identity.security.filter;

import com.aibos.identity.security.AibosUserDetails;
import com.aibos.identity.security.jwt.JwtClaims;
import com.aibos.identity.security.jwt.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs before every request.
 * Pipeline:
 *   1. Extract Bearer token from Authorization header
 *   2. Validate JWT signature
 *   3. Validate JWT expiration (handled by JJWT)
 *   4. Validate Redis blocklist (revoked tokens)
 *   5. Tier expiration validation handled separately in TierAuthorizationService
 *   6. Set authentication in SecurityContext (no DB call)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        try {
            JwtClaims claims = jwtService.parseAndValidate(token);
            AibosUserDetails principal = new AibosUserDetails(claims);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );
            auth.setDetails(new org.springframework.security.web.authentication
                    .WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (JwtException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            // Don't set auth — Spring Security will return 401 for protected endpoints
        }

        filterChain.doFilter(request, response);
    }
}

