package com.aibos.identity.service;


import com.aibos.identity.enums.Tier;

import static com.aibos.identity.enums.Tier.FREE;
import static com.aibos.identity.enums.Tier.PRO;

/**
 * Centralized tier feature matrix.
 * Using a record with static factory keeps limits in one place and avoids magic numbers.
 */
public record TierLimits(
        long maxProjects,
        long maxAnalysesPerMonth,
        boolean pdfExport,
        boolean sharing,
        int maxCompareReports,
        boolean whiteLabel,
        boolean apiAccess,
        int teamSeats,
        boolean priorityQueue,
        boolean slaSupport,
        boolean unlimitedProjects,
        boolean unlimitedAnalyses
) {
    public static final long UNLIMITED = Long.MAX_VALUE;

    public static TierLimits forTier(Tier tier) {
        return switch (tier) {
            case FREE -> new TierLimits(
                    3, 3, false, false, 0,
                    false, false, 0, false, false,
                    false, false
            );
            case PRO -> new TierLimits(
                    UNLIMITED, 50, true, true, 5,
                    false, false, 0, false, false,
                    true, false
            );
            case ENTERPRISE -> new TierLimits(
                    UNLIMITED, UNLIMITED, true, true, Integer.MAX_VALUE,
                    true, true, 5, true, true,
                    true, true
            );
        };
    }

    public boolean isAnalysisUnlimited() { return maxAnalysesPerMonth == UNLIMITED; }
    public boolean isProjectsUnlimited() { return maxProjects == UNLIMITED; }
}

