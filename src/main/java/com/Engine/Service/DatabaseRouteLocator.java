package com.Engine.Service;

import com.Engine.Entity.GatewayRoute;
import com.Engine.Repository.GatewayRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseRouteLocator implements RouteDefinitionLocator {
    private final GatewayRouteRepository gatewayRouteRepository;
    private final ObjectMapper objectMapper;
    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return gatewayRouteRepository.findAllActiveRoutes()
                .flatMap(r-> Mono.fromCallable(()->convertToRouteDefinition(r)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private RouteDefinition convertToRouteDefinition(GatewayRoute  gatewayRoute){
        RouteDefinition definition=new RouteDefinition();
        definition.setId(gatewayRoute.getRouteId());
        definition.setUri(URI.create(gatewayRoute.getUri()));

        try {
            if(gatewayRoute.getPredicatesJson()!=null){
                List<PredicateDefinition> predicates = objectMapper.readValue(gatewayRoute.getPredicatesJson(), new TypeReference<List<PredicateDefinition>>() {
                });
                definition.setPredicates(predicates);
            }
            if(gatewayRoute.getFiltersJson()!=null){
                List<FilterDefinition> filters=objectMapper.readValue(gatewayRoute.getFiltersJson(), new TypeReference<List<FilterDefinition>>() {
                });
                definition.setFilters(filters);
            }
        } catch (Exception e) {
            log.warn("CRITICAL: Failed to parse routing JSON for Route ID: {}", gatewayRoute.getRouteId(), e);
        }
        return definition;
    }
}
