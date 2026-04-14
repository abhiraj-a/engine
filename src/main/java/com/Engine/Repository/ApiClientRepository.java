package com.Engine.Repository;

import com.Engine.Entity.ApiClient;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ApiClientRepository extends ReactiveCrudRepository<ApiClient, UUID> {
    Mono<ApiClient> findByAuthifyerId(String clientId);
    Mono<ApiClient> findByClientId(String clientId);

    Flux<ApiClient> findAllByAuthifyerId(String sub);

    @Query("""
        UPDATE api_clients
        SET 
            current_tokens = LEAST(
                rate_limit_capacity::float, 
                current_tokens + (EXTRACT(EPOCH FROM (NOW() - last_refill_time)) * rate_limit_refill)
            ) - 1.0,
            last_refill_time = NOW()
        WHERE client_id = :clientId
          AND is_suspended = false
          AND LEAST(
                rate_limit_capacity::float, 
                current_tokens + (EXTRACT(EPOCH FROM (NOW() - last_refill_time)) * rate_limit_refill)
            ) >= 1.0
        RETURNING *;
    """)
    Mono<ApiClient> attemptConsumeToken(@Param("clientId") String clientId);
}
