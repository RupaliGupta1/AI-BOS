package com.aibos.identity.exception.custom;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidVerifyTokenException extends RuntimeException {
    public InvalidVerifyTokenException() { super("Invalid or expired verification token"); }
}
