package com.aibos.identity.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BillingRedirectController {

    @GetMapping("/billing/success")
    public ResponseEntity<String> success(@RequestParam(required = false) String session_id) {
        return ResponseEntity.ok("Payment successful! Session: " + session_id);
    }

    @GetMapping("/billing/cancel")
    public ResponseEntity<String> cancel() {
        return ResponseEntity.ok("Payment cancelled.");
    }
}