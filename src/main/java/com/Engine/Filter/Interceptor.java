package com.Engine.Filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Slf4j
@Component
public class Interceptor implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString();
        ServerHttpRequest original = exchange.getRequest();
        log.info("[⟶ INCOMING] ID: {} | {} {} | Client IP: {} | Headers: X-Client-Id={}",
                correlationId,
                original.getMethod(),
                original.getURI().getPath(),
                original.getRemoteAddress(),
                original.getHeaders().getFirst("X-Client-Id"));
        ServerHttpRequest mutated = original.mutate()
                .header("X-Correlation-Id",correlationId)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutated)
                .build();
        return chain.filter(mutatedExchange)
                .then(Mono.fromRunnable(()->{
                    long executeTime = System.currentTimeMillis()-startTime;
                    int statuscode=500;
                    if(mutatedExchange.getResponse().getStatusCode()!=null){
                       statuscode =  mutatedExchange.getResponse().getStatusCode().value();
                    }

                    // Log which route matched and where it was forwarded
                    Route route = mutatedExchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    String routeId = route != null ? route.getId() : "no-match";
                    String targetUri = route != null ? route.getUri().toString() : "none";

                    log.info("[⟵ OUTGOING] ID: {} | Status: {} | Latency: {}ms | Route: {} → {}",
                            correlationId, statuscode, executeTime, routeId, targetUri);
                }));
    }


    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
