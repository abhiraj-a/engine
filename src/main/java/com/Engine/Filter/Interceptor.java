package com.Engine.Filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

@Slf4j
@Component
public class Interceptor implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        ServerHttpRequest original = exchange.getRequest();

        String path = original.getURI().getPath();
        String query = original.getURI().getRawQuery();
        String fullPath = query != null ? path + "?" + query : path;
        String clientId = original.getHeaders().getFirst("X-Client-Id");
        String clientIp = original.getRemoteAddress() != null 
                ? original.getRemoteAddress().getAddress().getHostAddress() 
                : "unknown";

        log.info("[REQUEST-INBOUND] [Trace-ID: {}] {} {} | Client-IP: {} | Client-ID: {}",
                correlationId, original.getMethod(), fullPath, clientIp, clientId != null ? clientId : "ANONYMOUS");

        ServerHttpRequest mutated = original.mutate()
                .header("X-Correlation-Id", correlationId)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutated)
                .build();

        return chain.filter(mutatedExchange)
                .then(Mono.fromRunnable(() -> {
                    long latency = System.currentTimeMillis() - startTime;
                    HttpStatusCode statusCode = mutatedExchange.getResponse().getStatusCode();
                    int code = statusCode != null ? statusCode.value() : 500;

                    Route route = mutatedExchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    String routeId = route != null ? route.getId() : "unmatched";
                    URI actualUrl = mutatedExchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
                    String target = actualUrl != null ? actualUrl.toString() : (route != null ? route.getUri().toString() : "none");

                    String lbInstance = mutatedExchange.getResponse().getHeaders().getFirst("X-LB-Instance");
                    String rateLimitRemaining = mutatedExchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining");

                    log.info("[REQUEST-OUTBOUND] [Trace-ID: {}] Status: {} | Latency: {}ms | Route: [{}] | Destination: {}{}{}",
                            correlationId, code, latency, routeId, target,
                            lbInstance != null ? " | Upstream: " + lbInstance : "",
                            rateLimitRemaining != null ? " | RateLimit-Remaining: " + rateLimitRemaining : "");
                }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
