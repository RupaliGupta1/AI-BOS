package com.aibos.identity.service;

import com.aibos.identity.port.QuotaPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Temporary stub implementation of QuotaPort.
 * TODO: Replace with real implementation once the Analysis module
 * exposes project/analysis counts (via JPA repository, HTTP call, or gRPC).
 */
/**Then delete QuotaPortStub and replace it with this — Spring will pick up the
 *  new @Component automatically since both implement the same interface (just keep only one active implementation, or use @Primary/@ConditionalOnMissingBean if you want both to coexist for testing).
 *
 * Once you have a projects table and an analyses table (likely in the same
 * service, or a separate one), implement QuotaPort for real:*/
@Component
public class QuotaPortStub implements QuotaPort {

    @Override
    public long countProjectsByUser(UUID userId) {
        return 0L;
    }

    @Override
    public long countAnalysesThisMonthByUser(UUID userId) {
        return 0L;
    }
}

