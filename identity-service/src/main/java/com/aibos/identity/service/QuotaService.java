package com.aibos.identity.service;

import com.aibos.identity.dto.response.QuotaStatusResponse;
import com.aibos.identity.entity.User;
import com.aibos.identity.exception.custom.FeatureNotAvailableException;
import com.aibos.identity.exception.custom.QuotaExceededException;
import com.aibos.identity.port.QuotaPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Enforces per-tier quotas and feature gates.
 *
 * Call assertCanCreateProject() / assertCanRunAnalysis() before any such operation.
 * QuotaPort is implemented by the analysis bounded context (anti-corruption layer).
 */
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final QuotaPort quotaPort;

    /** Throws QuotaExceededException if the user cannot create another project. */
    public void assertCanCreateProject(User user) {
        TierLimits limits = TierLimits.forTier(user.getTier());
        if (limits.isProjectsUnlimited()) return;

        long used = quotaPort.countProjectsByUser(user.getId());
        if (used >= limits.maxProjects()) {
            throw new QuotaExceededException("projects", limits.maxProjects());
        }
    }

    /** Throws QuotaExceededException if the user cannot run another analysis this month. */
    public void assertCanRunAnalysis(User user) {
        TierLimits limits = TierLimits.forTier(user.getTier());
        if (limits.isAnalysisUnlimited()) return;

        long used = quotaPort.countAnalysesThisMonthByUser(user.getId());
        if (used >= limits.maxAnalysesPerMonth()) {
            throw new QuotaExceededException("analyses/month", limits.maxAnalysesPerMonth());
        }
    }

    /** Throws FeatureNotAvailableException if the feature requires a higher tier. */
    public void assertFeatureAvailable(User user, String feature) {
        TierLimits limits = TierLimits.forTier(user.getTier());
        boolean available = switch (feature) {
            case "pdf_export"    -> limits.pdfExport();
            case "sharing"       -> limits.sharing();
            case "compare"       -> limits.maxCompareReports() > 0;
            case "white_label"   -> limits.whiteLabel();
            case "api_access"    -> limits.apiAccess();
            case "priority_queue"-> limits.priorityQueue();
            default              -> false;
        };
        if (!available) throw new FeatureNotAvailableException(feature);
    }

    /** Current quota status for user dashboard. */
    public QuotaStatusResponse getQuotaStatus(User user) {
        TierLimits limits = TierLimits.forTier(user.getTier());
        UUID id = user.getId();
        return new QuotaStatusResponse(
                user.getTier(),
                quotaPort.countProjectsByUser(id),
                limits.isProjectsUnlimited() ? -1L : limits.maxProjects(),
                quotaPort.countAnalysesThisMonthByUser(id),
                limits.isAnalysisUnlimited() ? -1L : limits.maxAnalysesPerMonth(),
                limits.isProjectsUnlimited(),
                limits.isAnalysisUnlimited()
        );
    }
}

