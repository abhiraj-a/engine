package com.Engine.Entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("gateway_routes")
@Data
@Builder
public class GatewayRoute {

    @Id
    private UUID id;
    private String routeId;
    private String uri;
    private String predicatesJson;
    private String filtersJson;
    private int routeOrder;
    private boolean isActive=true;
    private String ownerId;
}
