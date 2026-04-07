package com.Engine.Repository;

import com.Engine.Entity.ApiClient;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ApiClientRepository extends ReactiveCrudRepository<ApiClient, UUID> {
    Mono<ApiClient> findByAuthifyerId(String clientId);
    Mono<ApiClient> findByClientId(String clientId);

    Flux<ApiClient> findAllByAuthifyerId(String sub);
}
