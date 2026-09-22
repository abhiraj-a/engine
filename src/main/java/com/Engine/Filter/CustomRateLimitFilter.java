package com.Engine.Filter;

import com.Engine.Service.ClientMetrics;
import com.Engine.Service.InMemoryRateLimitService;
import com.Engine.Service.LiveMetricsTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Component
public class CustomRateLimitFilter implements GlobalFilter, Ordered {

    private final InMemoryRateLimitService rateLimitService;
    private final LiveMetricsTracker metricsTracker;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Skip if already routed
        if(exchange.getAttributes().containsKey(ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR)){
            return chain.filter(exchange);
        }

        String clientId = exchange.getRequest().getHeaders().getFirst("X-Client-Id");
        if (clientId == null) {
            return chain.filter(exchange);
        }

        return rateLimitService.attemptConsume(clientId)
                .flatMap(result -> {
                    ClientMetrics metrics = metricsTracker.getClientMetrics(clientId);
                    if (result.allowed()) {
                        metrics.recordSuccess();
                        if (result.remainingTokens() >= 0) {
                            exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf((long) result.remainingTokens()));
                        }
                        return chain.filter(exchange);
                    } else {
                        metrics.recordFailure();
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", "0");
                        return exchange.getResponse().setComplete();
                    }
                });
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
