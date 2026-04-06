package com.Engine.Controller;

import com.Engine.DTO.GateWayRouteDTO;
import com.Engine.Entity.GatewayRoute;
import com.Engine.Repository.GatewayRouteRepository;
import com.Engine.Utils.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/admin/routes")
@RequiredArgsConstructor
public class AdminRouteController {

    private final GatewayRouteRepository gatewayRouteRepository;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping
    public ResponseEntity<?> getAllRoutes(@AuthenticationPrincipal Principal principal){
        return ResponseEntity.ok(gatewayRouteRepository.findAllActiveRoutes(principal.getSub()));
    }

    @PostMapping
    public Mono<?>  saveRoute(@AuthenticationPrincipal Principal principal,@RequestBody GateWayRouteDTO gatewayRoutedto){
            GatewayRoute gatewayRoute = GatewayRoute.builder()
                    .routeOrder(gatewayRoutedto.getRouteOrder())
                    .filtersJson(gatewayRoutedto.getFiltersJson())
                    .routeId(gatewayRoutedto.getRouteId())
                    .uri(gatewayRoutedto.getUri())
                    .isActive(true)
                    .ownerId(principal.getSub())
                    .predicatesJson(gatewayRoutedto.getPredicatesJson())
                    .build();
        return gatewayRouteRepository.save(gatewayRoute).doOnSuccess(route->{
            log.info("Route saved : {}" , route.getRouteId());
            eventPublisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("Gateway Memory Cache Refreshed!");
        }).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @DeleteMapping("/{routeId}")
    public Mono<?> deleteRoute(@AuthenticationPrincipal Principal principal,@PathVariable String routeId){
        return gatewayRouteRepository.deleteByRouteId(routeId)
                .doOnSuccess(unused -> {
                    log.info("Route Deleted from DB: {}", routeId);
                    eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                })
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
