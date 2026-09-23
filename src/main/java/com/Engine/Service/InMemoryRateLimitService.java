package com.Engine.Service;

import com.Engine.Repository.ApiClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Slf4j
@Service
public class InMemoryRateLimitService {
    private final ApiClientRepository apiClientRepository;
    private static final int MAX_CACHE_SIZE=10_000;

    public record RateLimitResult(boolean allowed, double remainingTokens) {}

    public Mono<RateLimitResult> attemptConsume(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Mono.just(new RateLimitResult(true, -1));
        }

        return apiClientRepository.attemptConsumeToken(clientId)
                .map(client -> {
                    log.info("[RATE-LIMITER] Token bucket updated: Client '{}' (Capacity: {}, Refill: {}/s) | Remaining balance: {}", 
                            client.getClientName() != null ? client.getClientName() : clientId,
                            client.getRateLimitCapacity(),
                            client.getRateLimitRefill(),
                            String.format("%.2f", client.getCurrentTokens()));
                    return new RateLimitResult(true, client.getCurrentTokens());
                })
                .defaultIfEmpty(new RateLimitResult(false, 0.0))
                .doOnNext(res -> {
                    if (!res.allowed()) {
                        log.warn("[RATE-LIMITER] Token deduction failed for Client [{}] (Tokens < 1.0 or client suspended)", clientId);
                    }
                });
    }

    public Mono<Boolean> isAllowed(String clientId) {
        return attemptConsume(clientId).map(RateLimitResult::allowed);
    }

    public Mono<Double> getLiveTokens(String clientId) {
        return apiClientRepository.findByClientId(clientId)
                .map(client -> {
                    long now = System.currentTimeMillis();
                    double elapsedTime = (now - client.getLastRefillTime().toEpochMilli()) / 1000.0;
                    return Math.min(
                            (double) client.getRateLimitCapacity(),
                            client.getCurrentTokens() + (client.getRateLimitRefill() * elapsedTime)
                    );
                }).defaultIfEmpty(0.0);
    }
}
