package com.Engine.DTO;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class MetricDTO {
    private double liveTokens;
    private long totalRequests;
    private long blockedRequests;
    private long passedRequests;
}
