package com.Engine.Controller;

import com.Engine.DTO.GateWayRouteDTO;
import com.Engine.Entity.GatewayRoute;
import com.Engine.Repository.GatewayRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/admin/routes")
@RequiredArgsConstructor
public class AdminRouteController {

    private final GatewayRouteRepository gatewayRouteRepository;
    private final ApplicationEventPublisher eventPublisher;

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
                // Replaced doOnSuccess (receives null Void signal, misleading) with
                // then(Mono.fromRunnable(...)) which is explicit about having no upstream value.
                .then(Mono.fromRunnable(() -> {
                    log.info("Route deleted from DB: {}", routeId);
                    eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                    log.info("Gateway route cache refreshed after delete");
                }))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
