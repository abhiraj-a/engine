package com.Engine.DTO;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class BackendHealthDTO {
    private String instanceId;
    private String url;
    private String state;
    private int activeConnections;
    private int consecutiveFailures;
    private Instant lastFailureTime;
}
