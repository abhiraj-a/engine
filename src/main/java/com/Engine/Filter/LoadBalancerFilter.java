package com.Engine.Filter;

import com.Engine.Service.LoadBalancerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Global filter that intercepts matched routes and rewrites the target URI
 * to a healthy backend instance selected by the LoadBalancerService.
 *
 * Runs BEFORE rate limiting and circuit breaking (order = -3).
 *
 * If a route has no backend instances registered, the original route URI is used
 * as a fallback (preserving backward compatibility).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoadBalancerFilter implements GlobalFilter, Ordered {

    private final LoadBalancerService loadBalancerService;

    private static final String LB_INSTANCE_ID_ATTR = "lb.instanceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Skip if already routed
        if (exchange.getAttributes().containsKey(ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR)) {
            return chain.filter(exchange);
        }

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }

        String routeId = route.getId();

        return loadBalancerService.selectInstance(routeId)
                .flatMap(instance -> {
                    // Rewrite the route URI to the selected backend instance
                    String instanceId = instance.getId().toString();
                    exchange.getAttributes().put(LB_INSTANCE_ID_ATTR, instanceId);

                    // Set the request URL to the selected backend
                    URI instanceUri = URI.create(instance.getUrl());
                    exchange.getAttributes().put(
                            ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, instanceUri);

                    // Add response header so caller can see which backend handled the request
                    exchange.getResponse().getHeaders().set("X-LB-Instance", instance.getUrl());

                    log.info("[LoadBalancer] Route [{}] → forwarding to {} (instance {})",
                            routeId, instance.getUrl(), instanceId);

                    loadBalancerService.incrementConnections(instanceId);

                    return chain.filter(exchange)
                            .then(Mono.<Void>fromRunnable(() -> recordOutcome(exchange, instanceId)));
                })
                // No backend instances found → fall through to the route's default URI
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));

    }

    private void recordOutcome(ServerWebExchange exchange, String instanceId) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        if (status != null && status.is5xxServerError()) {
            loadBalancerService.recordFailure(instanceId);
        } else {
            loadBalancerService.recordSuccess(instanceId);
        }
    }

    @Override
    public int getOrder() {
        // Before rate limiter (-1) and circuit breaker (-2)
        return -3;
    }
}
