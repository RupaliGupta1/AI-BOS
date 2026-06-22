package com.aibos.identity.exception.custom;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

/**
 * Thrown both when a project genuinely doesn't exist AND when it exists but
 * belongs to a different user. Deliberately the same exception/status (404) for
 * both cases — returning 403 for "exists but not yours" would leak the existence
 * of other users' project IDs to anyone who guesses/enumerates them.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException(UUID projectId) {
        super("Project not found: " + projectId);
    }
}
