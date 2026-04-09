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
        if(exchange.getAttributes().containsKey(ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR)){
           return chain.filter(exchange);
        }
        
        String clientId = exchange.getRequest().getHeaders().getFirst("X-Engine-Verified-Client");
        return rateLimitService.isAllowed(clientId)
                .flatMap(allowed->{
                    if(clientId!=null) {
                        ClientMetrics metrics = metricsTracker.getClientMetrics(clientId);
                        if ((boolean) allowed) {
                            metrics.recordSuccess();
                            return chain.filter(exchange);
                        }
                        else {
                            metrics.recordFailure();
                            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                            return exchange.getResponse().setComplete();
                        }

                    }
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
