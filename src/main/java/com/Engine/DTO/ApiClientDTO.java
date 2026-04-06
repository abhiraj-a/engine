package com.Engine.DTO;

public class ApiClientDTO {
    private String clientName;
    private Integer rateLimitRefill;
    private String clientId;
    private String jwksUrl;
    private Integer rateLimitCapacity;

    public String getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public String getJwksUrl() {
        return jwksUrl;
    }

    public Integer getRateLimitCapacity() {
        return rateLimitCapacity;
    }

    public Integer getRateLimitRefill() {
        return rateLimitRefill;
    }
}
