package com.Engine.Filter;

import com.Engine.Service.ClientMetrics;
import com.Engine.Service.InMemoryRateLimitService;
import com.Engine.Repository.ApiClientRepository;
import com.Engine.Service.LiveMetricsTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CustomRateLimitFilter implements GlobalFilter, Ordered {

    private final ApiClientRepository apiClientRepository;
    private final InMemoryRateLimitService rateLimitService;
    private final LiveMetricsTracker metricsTracker;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientId = exchange.getRequest().getHeaders().getFirst("X-Client-Id");
        return rateLimitService.isAllowed(clientId)
                .flatMap(allowed->{
                    ClientMetrics metrics = metricsTracker.getClientMetrics(clientId);
                    if((boolean) allowed){
                        metrics.recordSuccess();
                      return chain.filter(exchange);
                    }
                    metrics.recordFailure();
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return exchange.getResponse().setComplete();
                });
    }

    @Override
    public int getOrder() {
        return -10;
    }
}
