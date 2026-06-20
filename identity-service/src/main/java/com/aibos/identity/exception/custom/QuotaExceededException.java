package com.aibos.identity.exception.custom;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
public class QuotaExceededException extends RuntimeException {
    public QuotaExceededException(String resource, long limit) {
        super(String.format("Quota exceeded: %s limit is %d for your current plan", resource, limit));
    }
}
