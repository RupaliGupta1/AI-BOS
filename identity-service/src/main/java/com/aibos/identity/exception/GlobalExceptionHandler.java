package com.aibos.identity.exception;

import com.aibos.identity.exception.custom.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Maps domain exceptions to RFC 7807 Problem Details (Spring 6+ native support).
 * Returns structured JSON: { type, title, status, detail }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ProblemDetail handleEmailConflict(EmailAlreadyExistsException ex) {
        return problem(HttpStatus.CONFLICT, "Email Already Registered", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleBadCredentials(InvalidCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication Failed", ex.getMessage());
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ProblemDetail handleUnverified(EmailNotVerifiedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Email Not Verified", ex.getMessage());
    }

    @ExceptionHandler(InvalidVerifyTokenException.class)
    public ProblemDetail handleBadToken(InvalidVerifyTokenException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid Token", ex.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleBadRefresh(InvalidRefreshTokenException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid Refresh Token", ex.getMessage());
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ProblemDetail handleQuota(QuotaExceededException ex) {
        return problem(HttpStatus.PAYMENT_REQUIRED, "Quota Exceeded", ex.getMessage());
    }

    @ExceptionHandler(FeatureNotAvailableException.class)
    public ProblemDetail handleFeature(FeatureNotAvailableException ex) {
        return problem(HttpStatus.FORBIDDEN, "Feature Not Available", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Validation Failed", detail);
    }

    @ExceptionHandler(StripeWebhookException.class)
    public ProblemDetail handleStripe(StripeWebhookException ex) {
        log.warn("Stripe webhook error: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Webhook Error", ex.getMessage());
    }

    @ExceptionHandler(InvalidProjectStateTransitionException .class)
    public ProblemDetail handleStripe(InvalidProjectStateTransitionException  ex) {
        log.warn("Invalid Project State Transition: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Invalid Project State Transition", ex.getMessage());
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ProblemDetail handleStripe(ProjectNotFoundException ex) {
        log.warn("Project Not Found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, "Project Not Found", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("https://aibos.io/errors/" +
                title.toLowerCase().replace(' ', '-')));
        return pd;
    }
}
