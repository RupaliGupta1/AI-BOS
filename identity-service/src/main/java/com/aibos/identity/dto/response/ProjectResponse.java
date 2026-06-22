package com.aibos.identity.dto.response;

import com.aibos.identity.enums.ProjectStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        @JsonProperty("project_name") String projectName,
        @JsonProperty("idea_description") String ideaDescription,
        String industry,
        ProjectStatus status,
        @JsonProperty("analysis_version") Integer analysisVersion,
        @JsonProperty("cloned_from_project_id") UUID clonedFromProjectId,
        @JsonProperty("failure_reason") String failureReason,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {}