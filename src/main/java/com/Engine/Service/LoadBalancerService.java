package com.Engine.Service;

import com.Engine.Entity.BackendInstance;
import com.Engine.Entity.GatewayRoute;
import com.Engine.Repository.BackendInstanceRepository;
import com.Engine.Repository.GatewayRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core load balancer service.
 * Selects a healthy backend instance for a given route using the route's configured strategy.
 * Tracks instance health passively based on response outcomes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoadBalancerService {

    public enum Strategy {
        ROUND_ROBIN,
        LEAST_CONNECTIONS,
        WEIGHTED_ROUND_ROBIN,
        RANDOM
    }

    private final BackendInstanceRepository backendInstanceRepository;
    private final GatewayRouteRepository gatewayRouteRepository;

    // Health state per instance ID
    private final Map<String, InstanceHealth> healthMap = new ConcurrentHashMap<>();

    // Round-robin counters per route
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    /**
     * Select the best backend instance for the given route.
     * Returns Mono.empty() if the route has no backend instances (caller should fall back to the route's default URI).
     */
    public Mono<BackendInstance> selectInstance(String routeId) {
        return gatewayRouteRepository.findAllActiveRoutes()
                .filter(r -> r.getRouteId().equals(routeId))
                .next()
                .flatMap(route -> selectInstanceForRoute(route, routeId));
    }

    private Mono<BackendInstance> selectInstanceForRoute(GatewayRoute route, String routeId) {
        Strategy strategy = parseStrategy(route.getLbStrategy());

        return backendInstanceRepository.findByRouteIdAndIsActiveTrue(routeId)
                .collectList()
                .flatMap(allInstances -> {
                    if (allInstances.isEmpty()) {
                        log.info("[LOAD-BALANCER] [Route: {}] No dynamic backend instances configured. Forwarding to static route URI: {}",
                                routeId, route.getUri());
                        return Mono.empty();
                    }

                    List<BackendInstance> routableInstances = allInstances.stream()
                            .filter(instance -> getHealth(instance.getId().toString()).isRoutable())
                            .toList();

                    log.info("[LOAD-BALANCER] [Route: {}] Evaluating {} backend(s) with Strategy: {} (Healthy: {})",
                            routeId, allInstances.size(), strategy, routableInstances.size());

                    for (BackendInstance inst : allInstances) {
                        InstanceHealth h = getHealth(inst.getId().toString());
                        log.info("[LOAD-BALANCER] [Route: {}] Candidate: {} | Weight: {} | In-Flight: {} | State: {} | Failures: {}",
                                routeId, inst.getUrl(), inst.getWeight(), h.getActiveConnections(), h.getState(), h.getConsecutiveFailures());
                    }

                    if (routableInstances.isEmpty()) {
                        log.warn("[LOAD-BALANCER] [Route: {}] WARNING: All {} registered backend(s) are UNHEALTHY! Falling back to route default URI: {}",
                                routeId, allInstances.size(), route.getUri());
                        return Mono.empty();
                    }

                    BackendInstance selected = switch (strategy) {
                        case ROUND_ROBIN -> selectRoundRobin(routeId, routableInstances);
                        case LEAST_CONNECTIONS -> selectLeastConnections(routableInstances);
                        case WEIGHTED_ROUND_ROBIN -> selectWeightedRoundRobin(routeId, routableInstances);
                        case RANDOM -> selectRandom(routableInstances);
                    };

                    log.info("[LOAD-BALANCER] [Route: {}] Selected target instance: {} (Strategy: {})",
                            routeId, selected.getUrl(), strategy);
                    return Mono.just(selected);
                });
    }

    // ── Strategy Implementations ────────────────────────────────────────

    private BackendInstance selectRoundRobin(String routeId, List<BackendInstance> instances) {
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(routeId, k -> new AtomicInteger(0));
        int index = Math.abs(counter.getAndIncrement() % instances.size());
        return instances.get(index);
    }

    private BackendInstance selectLeastConnections(List<BackendInstance> instances) {
        BackendInstance best = instances.getFirst();
        int minConns = getHealth(best.getId().toString()).getActiveConnections();

        for (int i = 1; i < instances.size(); i++) {
            BackendInstance candidate = instances.get(i);
            int conns = getHealth(candidate.getId().toString()).getActiveConnections();
            if (conns < minConns) {
                minConns = conns;
                best = candidate;
            }
        }
        return best;
    }

    private BackendInstance selectWeightedRoundRobin(String routeId, List<BackendInstance> instances) {
        int totalWeight = instances.stream().mapToInt(BackendInstance::getWeight).sum();
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(routeId, k -> new AtomicInteger(0));
        int position = Math.abs(counter.getAndIncrement() % totalWeight);

        int cumulative = 0;
        for (BackendInstance instance : instances) {
            cumulative += instance.getWeight();
            if (position < cumulative) {
                return instance;
            }
        }
        // Fallback (shouldn't reach here)
        return instances.getFirst();
    }

    private BackendInstance selectRandom(List<BackendInstance> instances) {
        return instances.get(ThreadLocalRandom.current().nextInt(instances.size()));
    }

    // ── Health Recording ────────────────────────────────────────────────

    public InstanceHealth getHealth(String instanceId) {
        return healthMap.computeIfAbsent(instanceId, k -> new InstanceHealth());
    }

    public void recordSuccess(String instanceId) {
        InstanceHealth health = getHealth(instanceId);
        health.decrementConnections();
        health.recordSuccess();
    }

    public void recordFailure(String instanceId) {
        InstanceHealth health = getHealth(instanceId);
        health.decrementConnections();
        health.recordFailure();
        log.warn("[LOAD-BALANCER] Instance [{}] failure recorded. Consecutive failures: {}, State: {}",
                instanceId, health.getConsecutiveFailures(), health.getState());
    }

    public void incrementConnections(String instanceId) {
        getHealth(instanceId).incrementConnections();
    }

    /**
     * Get all health entries. Used by admin health endpoint.
     */
    public Map<String, InstanceHealth> getAllHealth() {
        return healthMap;
    }

    private Strategy parseStrategy(String value) {
        if (value == null || value.isBlank()) {
            return Strategy.ROUND_ROBIN;
        }
        try {
            return Strategy.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[LOAD-BALANCER] Unknown strategy '{}', falling back to ROUND_ROBIN", value);
            return Strategy.ROUND_ROBIN;
        }
    }
}
