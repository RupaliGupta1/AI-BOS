package com.aibos.identity.controller;

import com.aibos.identity.dto.response.QuotaStatusResponse;
import com.aibos.identity.dto.response.SubscriptionEventResponse;
import com.aibos.identity.dto.response.UserResponse;
import com.aibos.identity.entity.User;
import com.aibos.identity.security.AibosUserDetails;
import com.aibos.identity.service.QuotaService;
import com.aibos.identity.service.SubscriptionService;
import com.aibos.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * User profile, quota, and billing history API.
 * All endpoints require a valid JWT.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final QuotaService quotaService;

    @GetMapping
    public UserResponse profile(@AuthenticationPrincipal AibosUserDetails principal) {
        return userService.getProfile(principal.getUserId());
    }

    @GetMapping("/quota")
    public QuotaStatusResponse quota(@AuthenticationPrincipal AibosUserDetails principal) {
        User user = userService.findOrThrow(principal.getUserId());
        return quotaService.getQuotaStatus(user);
    }

    @GetMapping("/billing-history")
    public Page<SubscriptionEventResponse> billingHistory(
            @AuthenticationPrincipal AibosUserDetails principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return subscriptionService.getHistory(principal.getUserId(), pageable);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal AibosUserDetails principal) {
        userService.softDelete(principal.getUserId());
    }
}