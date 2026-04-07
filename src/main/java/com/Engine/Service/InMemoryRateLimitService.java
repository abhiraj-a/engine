package com.Engine.Service;

import com.Engine.Repository.ApiClientRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Slf4j
@Service
public class InMemoryRateLimitService {

    private Map<String, TokenBucket> bucketCache = new ConcurrentHashMap<>();
    private final ApiClientRepository apiClientRepository;

    public Mono<?> isAllowed(String clientId) {
        if (clientId == null) return Mono.just(true);
        if (bucketCache.containsKey(clientId)) {
            return Mono.just(bucketCache.get(clientId).
                    tryConsume());
        }
        return apiClientRepository.findByClientId(clientId)
                .map(client->{
                    TokenBucket tokenBucket =new TokenBucket(client.getRateLimitCapacity(), client.getRateLimitRefill(), client.getCurrentTokens(), client.getLastRefillTime());
                    bucketCache.put(clientId,tokenBucket);
                    return tokenBucket.tryConsume();
                }).defaultIfEmpty(true);
    }

    @PostConstruct
    public void loadBucketsFromDb(){
        apiClientRepository.findAll()
                .doOnNext(client->{
                    TokenBucket bucket = new TokenBucket(client.getRateLimitCapacity(),client.getRateLimitRefill(), client.getCurrentTokens(), client.getLastRefillTime());
                    bucketCache.put(client.getClientId(), bucket);
                })
                .subscribe(c -> log.info("Loaded rate limit state for: {}", c.getAuthifyerId()));
    }

    @PreDestroy
    public void persistBucketsToDb() {
        log.info("Shutting down: Persisting token counts to DB...");
        bucketCache.forEach((authifyerId,bucket)->{
            apiClientRepository.findByClientId(authifyerId)
                    .flatMap(client -> {
                        client.setCurrentTokens(bucket.getTokens());
                        client.setLastRefillTime(bucket.getLastRefillTime());
                        return apiClientRepository.save(client);
                    }).subscribe();
        });
    }

    public Mono<Double> getLiveTokens(String clientId){
        TokenBucket bucket = bucketCache.get(clientId);
        if(bucket!=null){
            return Mono.just(bucket.peek());
        }
        return apiClientRepository.findByClientId(clientId)
                .map(client->{
                    TokenBucket tokenBucket = new TokenBucket(
                            client.getRateLimitCapacity(),
                            client.getRateLimitRefill(),
                            client.getCurrentTokens(),
                            client.getLastRefillTime()
                    );
                    bucketCache.put(clientId,tokenBucket);
                    return tokenBucket.peek();
                }).defaultIfEmpty(0.0);
    }

    public TokenBucket getBucket(String clientId) {
        return bucketCache.get(clientId);
    }
}
