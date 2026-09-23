package com.Engine.Filter;

import com.Engine.Service.LoadBalancerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Global filter that rewrites the already-computed target URI to a healthy
 * backend instance selected by the LoadBalancerService.
 *
 * Runs AFTER Spring Cloud Gateway's RouteToRequestUrlFilter (order 10000)
 * so that GATEWAY_REQUEST_URL_ATTR is already populated with the full
 * target URL (route URI + request path). We then swap the host/scheme
 * portion with the selected backend while preserving the path and query.
 *
 * If a route has no backend instances registered, the original URL is
 * left untouched (preserving backward compatibility).
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

        // Read the URL that RouteToRequestUrlFilter already computed
        URI existingUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        if (existingUrl == null) {
            // RouteToRequestUrlFilter hasn't run yet — shouldn't happen at order 10001
            return chain.filter(exchange);
        }

        return loadBalancerService.selectInstance(routeId)
                .flatMap(instance -> {
                    String instanceId = instance.getId().toString();
                    exchange.getAttributes().put(LB_INSTANCE_ID_ATTR, instanceId);

                    // Swap the host/scheme but keep the path + query from the original URL
                    URI backendUri = URI.create(instance.getUrl());
                    URI rewrittenUrl = rebuildUri(existingUrl, backendUri);

                    exchange.getAttributes().put(
                            ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, rewrittenUrl);

                    // Add response header so caller can see which backend handled the request
                    exchange.getResponse().getHeaders().set("X-LB-Instance", instance.getUrl());

                    int activeConns = loadBalancerService.getHealth(instanceId).getActiveConnections() + 1;
                    log.info("[LOAD-BALANCER] [Route: {}] URL rewritten: {} -> {}", routeId, existingUrl, rewrittenUrl);
                    log.info("[LOAD-BALANCER] [Route: {}] Dispatching to upstream: {} | Active in-flight: {}",
                            routeId, instance.getUrl(), activeConns);

                    loadBalancerService.incrementConnections(instanceId);

                    return chain.filter(exchange)
                            .then(Mono.<Void>fromRunnable(() -> recordOutcome(exchange, instanceId, instance.getUrl(), routeId)))
                            .doOnError(throwable -> {
                                loadBalancerService.recordFailure(instanceId);
                                log.error("[LOAD-BALANCER] [Route: {}] Network transport error calling [{}]: {}",
                                        routeId, instance.getUrl(), throwable.getMessage());
                            });
                })
                // No backend instances → leave the original URL untouched
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("[LOAD-BALANCER] [Route: {}] No dynamic backends registered. Forwarding to default URI: {}",
                            routeId, existingUrl);
                    return chain.filter(exchange);
                }));
    }

    /**
     * Replace scheme, host, and port from the original URL with the backend's,
     * while preserving the original path and query string.
     */
    private URI rebuildUri(URI original, URI backend) {
        StringBuilder sb = new StringBuilder();
        sb.append(backend.getScheme()).append("://").append(backend.getHost());
        if (backend.getPort() > 0 && backend.getPort() != 443 && backend.getPort() != 80) {
            sb.append(':').append(backend.getPort());
        }
        if (backend.getRawPath() != null && !backend.getRawPath().isEmpty() && !"/".equals(backend.getRawPath())) {
            String basePath = backend.getRawPath().endsWith("/")
                    ? backend.getRawPath().substring(0, backend.getRawPath().length() - 1)
                    : backend.getRawPath();
            sb.append(basePath);
        }
        if (original.getRawPath() != null) {
            sb.append(original.getRawPath());
        }
        if (original.getRawQuery() != null) {
            sb.append('?').append(original.getRawQuery());
        }
        return URI.create(sb.toString());
    }

    private void recordOutcome(ServerWebExchange exchange, String instanceId, String backendUrl, String routeId) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        int remainingConns = Math.max(0, loadBalancerService.getHealth(instanceId).getActiveConnections() - 1);
        if (status != null && status.is5xxServerError()) {
            loadBalancerService.recordFailure(instanceId);
            log.warn("[LOAD-BALANCER] [Route: {}] Upstream [{}] returned 5xx Server Error: {} | Remaining in-flight: {}",
                    routeId, backendUrl, status.value(), remainingConns);
        } else {
            loadBalancerService.recordSuccess(instanceId);
            log.info("[LOAD-BALANCER] [Route: {}] Upstream [{}] response complete: {} | Remaining in-flight: {}",
                    routeId, backendUrl, status != null ? status.value() : 200, remainingConns);
        }
    }

    @Override
    public int getOrder() {
        // Run right AFTER RouteToRequestUrlFilter (order 10000)
        // so GATEWAY_REQUEST_URL_ATTR is already set with the full target URL
        return 10001;
    }
}
