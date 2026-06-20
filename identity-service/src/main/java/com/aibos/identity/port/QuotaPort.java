package com.aibos.identity.port;
import java.util.UUID;

/**
 * Quota enforcement port.
 * Implementations are expected to query the projects/analyses tables
 * (not in this domain module — they'll be provided by the analysis bounded context).
 *
 * For this domain module we provide the enforcement logic;
 * the quota counts are injected via these methods.
 */
public interface QuotaPort {
    long countProjectsByUser(UUID userId);
    long countAnalysesThisMonthByUser(UUID userId);
}
