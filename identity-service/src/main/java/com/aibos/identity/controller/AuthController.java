package com.aibos.identity.controller;

import com.aibos.identity.dto.request.LoginRequest;
import com.aibos.identity.dto.request.RefreshRequest;
import com.aibos.identity.dto.request.RegisterRequest;
import com.aibos.identity.dto.response.RegisterResponse;
import com.aibos.identity.dto.response.TokenResponse;
import com.aibos.identity.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication REST API.
 *
 * POST /api/v1/auth/register   — Create account
 * GET  /api/v1/auth/verify     — Email verification
 * POST /api/v1/auth/login      — Obtain tokens
 * POST /api/v1/auth/refresh    — Rotate refresh token
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @GetMapping("/verify")
    public ResponseEntity<String> verify(@RequestParam String token) {
        userService.verifyEmail(token);
        return ResponseEntity.ok("Email verified successfully. You may now log in.");
    }

    @PostMapping("/login")
    public TokenResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String deviceHint = httpRequest.getHeader("User-Agent");
        String ip = httpRequest.getRemoteAddr();
        return userService.login(request, deviceHint, ip);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        return userService.refresh(
                request.refreshToken(),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getRemoteAddr()
        );
    }
}

