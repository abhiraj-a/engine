package com.Engine.Entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("backend_instances")
@Data
@Builder
public class BackendInstance {
    @Id
    private UUID id;
    private String routeId;
    private String url;
    @Builder.Default
    private int weight = 1;
    @Builder.Default
    private boolean isActive = true;
    private Instant createdAt;
}
