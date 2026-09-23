package com.Engine.Repository;

import com.Engine.Entity.BackendInstance;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface BackendInstanceRepository extends ReactiveCrudRepository<BackendInstance, UUID> {

    @Query("SELECT * FROM backend_instances WHERE route_id = :routeId AND is_active = true")
    Flux<BackendInstance> findByRouteIdAndIsActiveTrue(@Param("routeId") String routeId);

    @Query("SELECT * FROM backend_instances WHERE route_id = :routeId")
    Flux<BackendInstance> findByRouteId(@Param("routeId") String routeId);

    @Modifying
    @Query("DELETE FROM backend_instances WHERE route_id = :routeId AND url = :url")
    Mono<Void> deleteByRouteIdAndUrl(@Param("routeId") String routeId, @Param("url") String url);
}
