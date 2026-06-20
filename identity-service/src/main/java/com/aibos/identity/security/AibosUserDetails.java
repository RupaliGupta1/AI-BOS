package com.aibos.identity.security;

import com.aibos.identity.entity.User;

import com.aibos.identity.enums.Tier;
import com.aibos.identity.security.jwt.JwtClaims;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security principal adapter.
 *
 * We support two construction modes:
 * 1. From a full User entity (after DB load) — used on login.
 * 2. From parsed JWT claims (no DB call) — used on every subsequent request.
 *
 * Authority convention: ROLE_FREE / ROLE_PRO / ROLE_ENTERPRISE
 * This enables @PreAuthorize("hasRole('PRO')") guards on controllers.
 */
@Getter
public class AibosUserDetails implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String passwordHash;
    private final Tier tier;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;
    private final String jti;    // JWT ID — for blocklist invalidation on logout

    /**
     * Full user entity constructor (login flow).
     */
    public AibosUserDetails(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.tier = user.getTier();
        this.enabled = user.isEmailVerified() && !user.isDeleted();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getTier().name()));
        this.jti = null;
    }

    /**
     * JWT claims constructor (every authenticated request — no DB hit).
     */
    public AibosUserDetails(JwtClaims claims) {
        this.userId = claims.userId();
        this.email = null;   // not in JWT to reduce payload size
        this.passwordHash = null;
        this.tier = claims.tier();
        this.enabled = true;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + claims.tier().name()));
        this.jti = claims.jti();
    }

    @Override
    public String getUsername() {
        return userId.toString();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

}