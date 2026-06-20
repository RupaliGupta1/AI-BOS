package com.aibos.identity.exception.custom;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(HttpStatus.FORBIDDEN)
public class FeatureNotAvailableException extends RuntimeException {
    public FeatureNotAvailableException(String feature) {
        super(String.format("Feature '%s' is not available on your current plan", feature));
    }
}
