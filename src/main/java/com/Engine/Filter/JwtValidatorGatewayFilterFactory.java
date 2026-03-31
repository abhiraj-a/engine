package com.Engine.Filter;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class JwtValidatorGatewayFilterFactory extends AbstractGatewayFilterFactory<JwtValidatorGatewayFilterFactory.Config> {

    private Map<String,JWKSource<SecurityContext>> jwkSourceMapCache =new ConcurrentHashMap<>();
    public JwtValidatorGatewayFilterFactory() {
        super(Config.class);
    }

    // Maps directly to the JSON provided by the developer in the webapp
    @Data
    public static class Config {
        private String jwksUrl;
        private String algorithm;
        private String claimForClientId;
        private String expectedIssuer;
        private String expectedAudience;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("jwksUrl", "algorithm", "claimForClientId", "expectedIssuer", "expectedAudience");
    }

    @Override
    public GatewayFilter apply(Config config) {

        JWKSource<SecurityContext> cachedKeySource = getOrCreateKeySource(config.getJwksUrl());

        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String token = authHeader.substring(7);

//            try {
//                SignedJWT signedJWT = SignedJWT.parse(token);
//
//                validateToken(signedJWT, config,cachedKeySource);
//                String clientId = extractClientId(signedJWT.getJWTClaimsSet(), config);
//                exchange.getRequest().mutate()
//                        .header("X-Engine-Verified-Client", clientId)
//                        .build();
//
//                return chain.filter(exchange);
//
//            } catch (Exception e) {
//                log.warn("JWT Validation failed for path [{}]: {}", exchange.getRequest().getPath(), e.getMessage());
//                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
//                return exchange.getResponse().setComplete();
//            }
            return Mono.fromCallable(()->{
                JWKSource<SecurityContext> keySource = getOrCreateKeySource(config.jwksUrl);
                SignedJWT signedJWT = SignedJWT.parse(token);
                validateToken(signedJWT,config,keySource);
                JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
                String clientId = extractClientId(jwtClaimsSet,config);
                return extractClientId(jwtClaimsSet,config);
            }).subscribeOn(Schedulers.boundedElastic())
                    .flatMap(clientId->{
                        exchange.getRequest().mutate()
                                .header("X-Engine-Verified-Client", clientId)
                                .build();
                        return chain.filter(exchange);
                    })
                    .onErrorResume(throwable -> {
                        log.warn("JWT Validation failed for path [{}]: {}", exchange.getRequest().getPath(), throwable.getMessage());
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    });
        };
    }

    private JWKSource<SecurityContext> getOrCreateKeySource(String jwksUrl)  {

//      if(jwkSourceMapCache.containsKey(jwksUrl)){
//          return jwkSourceMapCache.get(jwksUrl);
//      }
      jwkSourceMapCache.computeIfAbsent(jwksUrl , value->{
          JWKSource<SecurityContext> jwkSource = null;
          try {
              jwkSource = JWKSourceBuilder
                      .create(URI.create(jwksUrl).toURL())
                      .retrying(true)
                      .cache(15 * 60 * 1000, 5 * 60 * 1000)
                      .rateLimited(10 * 1000)
                      .build();
          } catch (MalformedURLException e) {
              throw new RuntimeException(e);
          }
          jwkSourceMapCache.put(jwksUrl,jwkSource);
          return jwkSource;
      });
//        JWKSource<SecurityContext> jwkSource = null;
//        try {
//            jwkSource = JWKSourceBuilder
//                    .create(URI.create(jwksUrl).toURL())
//                    .retrying(true)
//                    .build();
//        } catch (MalformedURLException e) {
//            throw new RuntimeException(e);
//        }
//        jwkSourceMapCache.put(jwksUrl,jwkSource);
      return jwkSourceMapCache.get(jwksUrl);
    }

    private void validateToken(SignedJWT signedJWT, Config config,JWKSource<SecurityContext> keySource) throws Exception {
        JWSAlgorithm expectedAlg = JWSAlgorithm.parse(config.getAlgorithm());
        if (!signedJWT.getHeader().getAlgorithm().equals(expectedAlg)) {
            throw new SecurityException("Algorithm mismatch. Expected: " + expectedAlg.getName());
        }

        var jwtProcessor = new DefaultJWTProcessor<SecurityContext>();
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(expectedAlg, keySource));
        JWTClaimsSet.Builder exactMatchClaims = new JWTClaimsSet.Builder();
        if (config.getExpectedIssuer() != null && !config.getExpectedIssuer().isBlank()) {
            exactMatchClaims.issuer(config.getExpectedIssuer());
        }
        Set<String> requiredClaims = new HashSet<>();
        if (config.getClaimForClientId() != null && !config.getClaimForClientId().isBlank()) {
            requiredClaims.add(config.getClaimForClientId());
        }

        Set<String> expectedAudience = null;
        if (config.getExpectedAudience() != null && !config.getExpectedAudience().isBlank()) {
            expectedAudience = new HashSet<>(Arrays.asList(config.getExpectedAudience()));
        }

        jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                expectedAudience,
                exactMatchClaims.build(),
                requiredClaims,
                null
        ));

        jwtProcessor.process(signedJWT, null);
    }

    private String extractClientId(JWTClaimsSet claims, Config config) throws ParseException {
        String expectedClaimName = config.getClaimForClientId();
        if (expectedClaimName == null || expectedClaimName.isBlank()) {
            throw new IllegalArgumentException("Engine Configuration Error: 'claimForClientId' is missing for this route.");
        }
        String clientId = claims.getStringClaim(expectedClaimName);
        if (clientId == null || clientId.isBlank()) {
            throw new SecurityException("Token rejected: Missing the explicitly required claim '" + expectedClaimName + "'.");
        }
        return clientId;
    }
}