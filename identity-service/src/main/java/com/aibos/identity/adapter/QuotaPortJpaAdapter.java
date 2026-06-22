package com.aibos.identity.adapter;

import com.aibos.identity.port.QuotaPort;
import com.aibos.identity.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Real QuotaPort implementation, backed by the projects table.
 *
 * REPLACES QuotaPortStub — delete that class as part of this change.
 * Spring will fail to start with an ambiguous bean error if both remain
 * registered as @Component implementations of the same interface.
 *
 * countAnalysesThisMonthByUser() is intentionally still stubbed at 0 — the
 * agent_executions table doesn't exist yet (Analysis Engine milestone).
 * This is an honest stub, not a fake number: it means the monthly-analysis
 * quota currently never blocks anyone, which is correct until that table exists.
 */
@Component
@RequiredArgsConstructor
public class QuotaPortJpaAdapter implements QuotaPort {

    private final ProjectRepository projectRepository;

    @Override
    public long countProjectsByUser(UUID userId) {
        return projectRepository.countByUserIdAndArchivedAtIsNull(userId);
    }

    @Override
    public long countAnalysesThisMonthByUser(UUID userId) {
        // TODO: replace once agent_executions table exists (Analysis Engine milestone).
        // See SQL reference:
        //   SELECT COUNT(*) FROM agent_executions ae
        //   JOIN projects p ON ae.project_id = p.id
        //   WHERE p.user_id = :userId
        //     AND ae.started_at >= DATE_TRUNC('month', NOW())
        //     AND ae.status = 'COMPLETED'
        return 0L;
    }
}