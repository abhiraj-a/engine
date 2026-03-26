package com.Engine.Repository;

import com.Engine.Entity.ApiClient;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ApiClientRepository extends ReactiveCrudRepository<ApiClient, UUID> {
    Mono<ApiClient> findByClientId(String clientId);
}
