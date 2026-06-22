package com.aibos.identity.controller;

import com.aibos.identity.dto.request.CreateProjectRequest;
import com.aibos.identity.dto.response.ProjectResponse;
import com.aibos.identity.security.AibosUserDetails;
import com.aibos.identity.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Project Management REST API.
 *
 * POST   /api/v1/projects        — create
 * GET    /api/v1/projects        — list (paginated, reverse chronological, 20/page default)
 * GET    /api/v1/projects/{id}   — get single
 *
 * Archive (DELETE) and Clone (POST .../clone) are added in Step 5.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(
            @AuthenticationPrincipal AibosUserDetails principal,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        return projectService.createProject(principal.getUserId(), request);
    }

    @GetMapping
    public Page<ProjectResponse> listProjects(
            @AuthenticationPrincipal AibosUserDetails principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return projectService.listProjects(principal.getUserId(), pageable);
    }

    @GetMapping("/{id}")
    public ProjectResponse getProject(
            @AuthenticationPrincipal AibosUserDetails principal,
            @PathVariable UUID id
    ) {
        return projectService.getProject(principal.getUserId(), id);
    }
}