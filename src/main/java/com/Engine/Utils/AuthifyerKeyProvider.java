/*
package com.Engine.Utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Component
public class AuthifyerKeyProvider {


    private final Map<String, PublicKey> cache = new ConcurrentHashMap<>();

    private static final long TTL_MS = 3_600_000L;
    private volatile long lastFetchTime = 0;

    // FIX: Holds the in-flight fetch Mono so concurrent cold-cache requests share
    // one HTTP call instead of each firing their own. Volatile for visibility.
    private volatile Mono<Void> inflightFetch = null;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://authifyer-backend.onrender.com")
            .build();

    public Mono<PublicKey> getPublicKey(String kid) {
        if (cache.containsKey(kid) && System.currentTimeMillis() - lastFetchTime < TTL_MS) {
            log.debug("JWK cache hit for kid={}", kid);
            return Mono.just(cache.get(kid));
        }

        return refreshKeys().then(Mono.defer(() -> {
            PublicKey key = cache.get(kid);
            if (key == null) {
                return Mono.<PublicKey>error(
                        new SecurityException("Unknown kid '" + kid + "' — not present in JWKS after refresh"));
            }
            return Mono.just(key);
        }));
    }

    // Synchronized so that concurrent requests share one in-flight Mono
    // rather than each triggering a separate remote fetch.
    private synchronized Mono<Void> refreshKeys() {
        if (inflightFetch != null) {
            return inflightFetch;
        }
        inflightFetch = webClient.get()
                .uri("/authifyer/.well-known/jwks.json")
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                .flatMap(jwks -> {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode keys = mapper.readTree(jwks).get("keys");
                        if (keys != null) {
                            for (JsonNode key : keys) {
                                String kid = key.get("kid").asText();
                                PublicKey publicKey = createPublicKey(
                                        key.get("n").asText(),
                                        key.get("e").asText());
                                cache.put(kid, publicKey);
                            }
                        }
                        lastFetchTime = System.currentTimeMillis();
                        log.info("JWK cache refreshed — {} keys loaded", cache.size());
                        return Mono.<Void>empty();
                    } catch (Exception ex) {
                        return Mono.error(ex);
                    }
                })
                .doFinally(signal -> {
                    synchronized (AuthifyerKeyProvider.this) {
                        inflightFetch = null;
                    }
                })
                .cache(); // share among concurrent subscribers

        return inflightFetch;
    }

    private PublicKey createPublicKey(String n, String e) throws Exception {
        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
        return KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }
}
*/

