package com.Engine.DTO;

import lombok.Builder;

@Builder
public class MetricDTO {
    private double liveTokens;
    private long totalRequests;
    private long blockedRequests;
    private long passedRequests;
}
