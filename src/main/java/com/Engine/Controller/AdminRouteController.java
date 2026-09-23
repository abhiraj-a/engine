package com.Engine.Controller;

import com.Engine.DTO.BackendHealthDTO;
import com.Engine.DTO.BackendInstanceDTO;
import com.Engine.DTO.GateWayRouteDTO;
import com.Engine.Entity.BackendInstance;
import com.Engine.Entity.GatewayRoute;
import com.Engine.Repository.BackendInstanceRepository;
import com.Engine.Repository.GatewayRouteRepository;
import com.Engine.Service.InstanceHealth;
import com.Engine.Service.LoadBalancerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/routes")
@RequiredArgsConstructor
public class AdminRouteController {

    private final GatewayRouteRepository gatewayRouteRepository;
    private final BackendInstanceRepository backendInstanceRepository;
    private final LoadBalancerService loadBalancerService;
    private final ApplicationEventPublisher eventPublisher;

    // ── Route CRUD ──────────────────────────────────────────────────────

    @GetMapping
    public Flux<GatewayRoute> getAllRoutes() {
        return gatewayRouteRepository.findAllActiveRoutes();
    }

    @PostMapping
    public Mono<?> saveRoute(@RequestBody GateWayRouteDTO gatewayRoutedto) {
        GatewayRoute gatewayRoute = GatewayRoute.builder()
                .routeOrder(gatewayRoutedto.getRouteOrder())
                .filtersJson(gatewayRoutedto.getFiltersJson())
                .routeId(gatewayRoutedto.getRouteId())
                .uri(gatewayRoutedto.getUri())
                .isActive(true)
                .ownerId("default")
                .predicatesJson(gatewayRoutedto.getPredicatesJson())
                .lbStrategy(gatewayRoutedto.getLbStrategy() != null
                        ? gatewayRoutedto.getLbStrategy() : "ROUND_ROBIN")
                .build();
        return gatewayRouteRepository.save(gatewayRoute).doOnSuccess(route -> {
            log.info("Route saved : {}", route.getRouteId());
            eventPublisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("Gateway Memory Cache Refreshed!");
        }).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @DeleteMapping("/{routeId}")
    public Mono<ResponseEntity<Void>> deleteRoute(@PathVariable String routeId) {
        return gatewayRouteRepository.deleteByRouteId(routeId)
                .then(Mono.fromRunnable(() -> {
                    log.info("Route deleted from DB: {}", routeId);
                    eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                    log.info("Gateway route cache refreshed after delete");
                }))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    // ── Load Balancing Strategy ─────────────────────────────────────────

    @PutMapping("/{routeId}/lb-strategy")
    public Mono<ResponseEntity<GatewayRoute>> updateLbStrategy(
            @PathVariable String routeId,
            @RequestParam String strategy) {
        return gatewayRouteRepository.findAllActiveRoutes()
                .filter(r -> r.getRouteId().equals(routeId))
                .next()
                .flatMap(route -> {
                    route.setLbStrategy(strategy.toUpperCase());
                    return gatewayRouteRepository.save(route);
                })
                .doOnSuccess(r -> {
                    if (r != null) {
                        log.info("Updated LB strategy for route [{}] to {}", routeId, strategy);
                        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                    }
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ── Backend Instance Management ─────────────────────────────────────

    @GetMapping("/{routeId}/backends")
    public Flux<BackendInstance> getBackends(@PathVariable String routeId) {
        return backendInstanceRepository.findByRouteId(routeId);
    }

    @PostMapping("/{routeId}/backends")
    public Mono<ResponseEntity<BackendInstance>> addBackend(
            @PathVariable String routeId,
            @RequestBody BackendInstanceDTO dto) {
        BackendInstance instance = BackendInstance.builder()
                .routeId(routeId)
                .url(dto.getUrl())
                .weight(dto.getWeight() > 0 ? dto.getWeight() : 1)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
        return backendInstanceRepository.save(instance)
                .doOnSuccess(saved -> log.info("Backend instance added for route [{}]: {}",
                        routeId, saved.getUrl()))
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{routeId}/backends/{instanceId}")
    public Mono<ResponseEntity<Void>> removeBackend(
            @PathVariable String routeId,
            @PathVariable java.util.UUID instanceId) {
        return backendInstanceRepository.deleteById(instanceId)
                .then(Mono.fromRunnable(() ->
                        log.info("Backend instance [{}] removed from route [{}]", instanceId, routeId)))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    // ── Backend Health ──────────────────────────────────────────────────

    @GetMapping("/{routeId}/backends/health")
    public Flux<BackendHealthDTO> getBackendHealth(@PathVariable String routeId) {
        return backendInstanceRepository.findByRouteId(routeId)
                .map(instance -> {
                    InstanceHealth health = loadBalancerService.getHealth(instance.getId().toString());
                    return BackendHealthDTO.builder()
                            .instanceId(instance.getId().toString())
                            .url(instance.getUrl())
                            .state(health.getState().name())
                            .activeConnections(health.getActiveConnections())
                            .consecutiveFailures(health.getConsecutiveFailures())
                            .lastFailureTime(health.getLastFailureTime())
                            .build();
                });
    }
}

