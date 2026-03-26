package com.Engine.Entity;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;


@Table("api_clients")
@Data
@Builder
public class ApiClient {
    @Id
    private UUID id;
    private String clientName;
    private String clientId;
    private String jwksUrl;
    private Integer rateLimitCapacity;
    private Integer rateLimitRefill;
    private boolean isSuspended;
    private double currentTokens;
    private Instant lastRefillTime;
}
