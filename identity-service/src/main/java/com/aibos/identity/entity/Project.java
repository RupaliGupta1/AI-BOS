package com.aibos.identity.entity;

import com.aibos.identity.enums.ProjectStatus;
import com.aibos.identity.exception.custom.InvalidProjectStateTransitionException;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * User-owned business analysis project.
 *
 * Lifecycle is enforced via transitionTo() — never set `status` directly
 * from service code. This keeps the state machine rules in one place
 * (the domain object) rather than scattered across services/controllers/
 * future RabbitMQ consumers.
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Project {

    private static final Map<ProjectStatus, Set<ProjectStatus>> ALLOWED_TRANSITIONS = Map.of(
            ProjectStatus.CREATED, Set.of(ProjectStatus.QUEUED),
            ProjectStatus.QUEUED,  Set.of(ProjectStatus.RUNNING),
            ProjectStatus.RUNNING, Set.of(ProjectStatus.COMPLETED, ProjectStatus.FAILED, ProjectStatus.PARTIAL)
            // COMPLETED, FAILED, PARTIAL are terminal — intentionally absent as map keys
    );

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "project_name", nullable = false, length = 100)
    private String projectName;

    @Column(name = "idea_description", nullable = false, length = 2000)
    private String ideaDescription;

    @Column(name = "industry", length = 100)
    private String industry;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.CREATED;

    @Column(name = "analysis_version", nullable = false)
    @Builder.Default
    private Integer analysisVersion = 1;

    @Column(name = "cloned_from_project_id")
    private UUID clonedFromProjectId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    /** Optimistic locking — prevents lost updates when RabbitMQ workers and user actions race. */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Domain behavior ──────────────────────────────────────────────────────

    /**
     * Validates and applies a status transition according to the lifecycle rules.
     * This is the ONLY sanctioned way to change status — never call setStatus() directly
     * from service code.
     *
     * @throws InvalidProjectStateTransitionException if the transition isn't allowed
     */
    public void transitionTo(ProjectStatus newStatus) {
        Set<ProjectStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(this.status, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new InvalidProjectStateTransitionException(this.status, newStatus);
        }
        this.status = newStatus;
    }

    /** Convenience for transitioning into a terminal failure state with a reason. */
    public void fail(String reason) {
        transitionTo(ProjectStatus.FAILED);
        this.failureReason = reason;
    }

    /** Convenience for transitioning into PARTIAL with a reason (some agents succeeded, some didn't). */
    public void partial(String reason) {
        transitionTo(ProjectStatus.PARTIAL);
        this.failureReason = reason;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public void archive() {
        this.archivedAt = Instant.now();
    }
}