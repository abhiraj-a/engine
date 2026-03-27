package com.Engine.ControlPane.Controller;

import com.Engine.Entity.GatewayRoute;
import com.Engine.Repository.GatewayRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<?> getAllRoutes(){
        return ResponseEntity.ok(gatewayRouteRepository.findAllActiveRoutes());
    }

    @PostMapping
    public Mono<?>  saveRoute(@RequestBody GatewayRoute gatewayRoute){
        return gatewayRouteRepository.save(gatewayRoute).doOnSuccess(route->{
            log.info("Route saved : {}" , route.getRouteId());
            eventPublisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("Gateway Memory Cache Refreshed!");
        }).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @DeleteMapping("/{routeId}")
    public Mono<?> deleteRoute(@PathVariable String routeId){
        return gatewayRouteRepository.deleteByRouteId(routeId)
                .doOnSuccess(unused -> {
                    log.info("Route Deleted from DB: {}", routeId);
                    eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                })
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
