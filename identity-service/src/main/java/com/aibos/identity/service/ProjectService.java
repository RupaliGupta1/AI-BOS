package com.aibos.identity.service;

import com.aibos.identity.dto.request.CreateProjectRequest;
import com.aibos.identity.dto.response.ProjectResponse;
import com.aibos.identity.entity.Project;
import com.aibos.identity.entity.User;
import com.aibos.identity.enums.ProjectStatus;
import com.aibos.identity.exception.custom.ProjectNotFoundException;
import com.aibos.identity.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Core project CRUD + lifecycle orchestration.
 *
 * Quota enforcement (QuotaService.assertCanCreateProject) runs before every
 * project creation. The underlying QuotaPort implementation (QuotaPortJpaAdapter)
 * counts real rows in this table — FREE tier's 3-project limit is fully live.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserService userService;
    private final QuotaService quotaService;

    // ── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public ProjectResponse createProject(UUID userId, CreateProjectRequest request) {
        User user = userService.findOrThrow(userId);

        quotaService.assertCanCreateProject(user);

        Project project = Project.builder()
                .user(user)
                .projectName(request.projectName().trim())
                .ideaDescription(request.ideaDescription().trim())
                .industry(request.industry() != null ? request.industry().trim() : null)
                .status(ProjectStatus.CREATED)
                .build();

        project = projectRepository.save(project);
        log.info("Project created: id={} user={} name='{}'",
                project.getId(), userId, project.getProjectName());

        return toResponse(project);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID userId, UUID projectId) {
        Project project = findOwnedProjectOrThrow(userId, projectId);
        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> listProjects(UUID userId, Pageable pageable) {
        return projectRepository
                .findByUserIdAndArchivedAtIsNullOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    // ── Shared ownership-check helper ───────────────────────────────────────
    // Centralized here so every method below (and the ones added in Step 5 —
    // archive, clone) uses the exact same "owned or 404" semantics rather than
    // duplicating the check across the class.

    protected Project findOwnedProjectOrThrow(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private ProjectResponse toResponse(Project p) {
        return new ProjectResponse(
                p.getId(),
                p.getProjectName(),
                p.getIdeaDescription(),
                p.getIndustry(),
                p.getStatus(),
                p.getAnalysisVersion(),
                p.getClonedFromProjectId(),
                p.getFailureReason(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}