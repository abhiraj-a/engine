package com.Engine.Filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Slf4j
public class Interceptor implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        long startTime = System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString();
        ServerHttpRequest original = exchange.getRequest();
        log.info("[INCOMING] ID: {} | Method: {} | Path: {} | Client IP: {}",
                correlationId,
                original.getMethod(),
                original.getURI().getPath(),
                original.getRemoteAddress());
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
                    log.info("[OUTGOING] ID: {} | Status: {} | Latency: {}ms",
                            correlationId, statuscode, executeTime);
                }));
    }
    

}
