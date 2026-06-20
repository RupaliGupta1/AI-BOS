package com.aibos.identity.exception.custom;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class StripeWebhookException extends RuntimeException {
    public StripeWebhookException(String msg) { super(msg); }
}
