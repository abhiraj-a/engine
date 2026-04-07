package com.Engine.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

public class LiveMetricsTracker {

    private final Cache<String, ClientMetrics> metricsCache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(50_000)
            .build();

    public ClientMetrics getClientMetrics(String clientId) {
        return metricsCache.get(clientId, k -> new ClientMetrics());
    }
}
