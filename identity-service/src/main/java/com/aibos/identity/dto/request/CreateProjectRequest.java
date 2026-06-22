package com.aibos.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Project creation payload.
 * Length constraints are mirrored at the DB level (chk_project_name_length,
 * chk_idea_description_length) as defense in depth — validation should never
 * rely solely on the application layer.
 */
public record CreateProjectRequest(
        @NotBlank @Size(max = 100) String projectName,
        @NotBlank @Size(max = 2000) String ideaDescription,
        @Size(max = 100) String industry   // optional
) {}