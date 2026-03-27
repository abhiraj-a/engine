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

import java.net.URI;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class JwtValidatorGatewayFilterFactory extends AbstractGatewayFilterFactory<JwtValidatorGatewayFilterFactory.Config> {

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
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String token = authHeader.substring(7);

            try {
                SignedJWT signedJWT = SignedJWT.parse(token);

                // 2. Validate cryptographically against the Developer's strict Config
                validateToken(signedJWT, config);

                // 3. Extract identity EXPLICITLY based on the Developer's chosen claim
                String clientId = extractClientId(signedJWT.getJWTClaimsSet(), config);

                // 4. Inject secure header for the Rate Limiter and downstream microservices
                exchange.getRequest().mutate()
                        .header("X-Engine-Verified-Client", clientId)
                        .build();

                return chain.filter(exchange);

            } catch (Exception e) {
                log.warn("JWT Validation failed for path [{}]: {}", exchange.getRequest().getPath(), e.getMessage());
                // Note: We currently just return a blank 401. We will fix this with the Exception Handler.
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
        };
    }

    private void validateToken(SignedJWT signedJWT, Config config) throws Exception {
        JWSAlgorithm expectedAlg = JWSAlgorithm.parse(config.getAlgorithm());
        if (!signedJWT.getHeader().getAlgorithm().equals(expectedAlg)) {
            throw new SecurityException("Algorithm mismatch. Expected: " + expectedAlg.getName());
        }
        JWKSource<SecurityContext> keySource = JWKSourceBuilder
                .create(URI.create(config.getJwksUrl()).toURL())
                .retrying(true)
                .build();
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