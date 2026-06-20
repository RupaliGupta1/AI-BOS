package com.aibos.identity.dto.response;
import com.aibos.identity.enums.Tier;
import com.fasterxml.jackson.annotation.JsonProperty;

public record QuotaStatusResponse(
        Tier tier,
        @JsonProperty("projects_used") long projectsUsed,
        @JsonProperty("projects_limit") long projectsLimit,
        @JsonProperty("analyses_used_this_month") long analysesUsedThisMonth,
        @JsonProperty("analyses_limit") long analysesLimit,
        @JsonProperty("projects_unlimited") boolean projectsUnlimited,
        @JsonProperty("analyses_unlimited") boolean analysesUnlimited
) {}

