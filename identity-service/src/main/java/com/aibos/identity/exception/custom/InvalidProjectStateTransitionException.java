package com.aibos.identity.exception.custom;

import com.aibos.identity.enums.ProjectStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidProjectStateTransitionException extends RuntimeException {
    public InvalidProjectStateTransitionException(ProjectStatus from, ProjectStatus to) {
        super(String.format("Cannot transition project from %s to %s", from, to));
    }
}




