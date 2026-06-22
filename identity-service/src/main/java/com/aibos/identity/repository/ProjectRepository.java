package com.aibos.identity.repository;

import com.aibos.identity.entity.Project;
import com.aibos.identity.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /** List a user's active (non-archived) projects, reverse chronological, paginated. */
    Page<Project> findByUserIdAndArchivedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Single project ownership-scoped lookup. */
    Optional<Project> findByIdAndUserId(UUID id, UUID userId);

    /** Quota check: count active (non-archived) projects for a user. */
    long countByUserIdAndArchivedAtIsNull(UUID userId);

    /** Future RabbitMQ worker use: poll all projects in a given status across all users. */
    List<Project> findByStatus(ProjectStatus status);
}
