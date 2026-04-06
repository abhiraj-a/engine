package com.Engine.Repository;

import com.Engine.Entity.GatewayRoute;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GatewayRouteRepository extends ReactiveCrudRepository<GatewayRoute, UUID> {
    //  CUSTOM QUERY: Fetch only the routes that are currently active.
    // Notice it returns a Flux because it will stream multiple rows back asynchronously.
    @Query("SELECT * FROM gateway_routes WHERE is_active  = true AND ownerId = :ownerId ORDER BY route_order ASC")
    Flux<GatewayRoute> findAllActiveRoutes(@Param("ownerId")String ownerId);

    // CUSTOM QUERY: Find a specific route by its URI.
    // Returns a Mono because we only expect 0 or 1 result.
    @Query("SELECT * FROM gateway_routes WHERE uri = :uri LIMIT 1")
    Mono<GatewayRoute> findByUri(String uri);

    @Modifying
    @Query("DELETE FROM gateway_routes WHERE route_Id = :routeId")
    Mono<Object> deleteByRouteId(@Param("routeId") String routeId);
}
