package com.Engine.Service;

import com.Engine.Repository.ApiClientRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Slf4j
@Service
public class InMemoryRateLimitService {
    private final ApiClientRepository apiClientRepository;
    private static final int MAX_CACHE_SIZE=100_00;

    public Mono<Boolean> isAllowed(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Mono.just(true); // Allow anonymous traffic. Change to Mono.just(false) to block it.
        }

        // The SQL query will ONLY return a row if it successfully consumed a token.
        // If it returns empty, it means they had 0 tokens (Rate Limited) or an invalid ID.
        return apiClientRepository.attemptConsumeToken(clientId)
                .map(client -> true)
                .defaultIfEmpty(false);
    }

    public Mono<Double> getLiveTokens(String clientId) {
        return apiClientRepository.findByClientId(clientId)
                .map(client -> {
                    // Calculate what the tokens *would* be right now for your UI stream
                    long now = System.currentTimeMillis();
                    double elapsedTime = (now - client.getLastRefillTime().toEpochMilli()) / 1000.0;
                    return Math.min(
                            (double) client.getRateLimitCapacity(),
                            client.getCurrentTokens() + (client.getRateLimitRefill() * elapsedTime)
                    );
                }).defaultIfEmpty(0.0);
    }
}
