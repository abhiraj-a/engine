package com.Engine.Entity;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

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
    private String authifyerId;
    @Nullable
    private String jwksUrl;
    private Integer rateLimitCapacity;
    private Integer rateLimitRefill;
    @Builder.Default
    private boolean isSuspended=false;
    private double currentTokens;
    private Instant lastRefillTime;

}
