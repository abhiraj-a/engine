package com.Engine.DTO;

import lombok.Builder;
import lombok.Getter;
import org.springframework.lang.Nullable;

@Builder
@Getter
public class ApiClientRespone {

    private String clientName;
    private String clientId;
    private String authifyerId;
    @Nullable
    private String jwksUrl;
    private boolean isSuspended;
    private double currentTokens;

}
