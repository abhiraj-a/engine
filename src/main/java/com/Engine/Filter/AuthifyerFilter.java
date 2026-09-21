/*
package com.Engine.Filter;
import com.Engine.Utils.AuthifyerKeyProvider;
import com.Engine.Utils.Principal;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Collections;

@RequiredArgsConstructor
@Component
@Slf4j
public class AuthifyerFilter implements WebFilter {

    private final AuthifyerKeyProvider provider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // No token — pass through.
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);
        String kid = extractKid(token);

        // No kid in the JWT header — not an Authifyer token, pass through.
        if (kid == null) {
            return chain.filter(exchange);
        }

        // Try to verify as an Authifyer token. If anything goes wrong at any point
        // (kid not in JWKS, wrong issuer, expired, bad signature) just pass through —
        // it simply means the token belongs to a different auth system.

            return provider.getPublicKey(kid)
                    .flatMap(publicKey -> {
                        try {
                            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) publicKey, null);
                            JWTVerifier verifier = JWT.require(algorithm)
                                    .withIssuer("https://authifyer-backend.onrender.com")
                                    .acceptLeeway(5)
                                    .build();

                            DecodedJWT decodedJWT = verifier.verify(token);
                            String sub = decodedJWT.getClaim("sub").asString();
                            String email = decodedJWT.getClaim("email").asString();
                            Principal principal = new Principal(sub, email);

                            UsernamePasswordAuthenticationToken authToken =
                                    new UsernamePasswordAuthenticationToken(
                                            principal, null, Collections.emptyList());

                            log.debug("Authifyer user authenticated: sub={} path={}", sub, path);
                            return chain.filter(exchange)
                                    .contextWrite(ReactiveSecurityContextHolder
                                            .withAuthentication(authToken));
                        } catch (Exception e) {
                            // Verification failed — not our token, pass through.
                            log.debug("Token not from Authifyer on path={}, passing through: {}", path, e.getMessage());
                            return chain.filter(exchange);
                        }
                    })
                    .onErrorResume(e -> {
                        // Key lookup failed — not our token, pass through.
                        log.debug("kid={} not in Authifyer JWKS on path={}, passing through", kid, path);
                        return chain.filter(exchange);
                    });

    }


    private String extractKid(String token) {
        try {
            String[] chunks = token.split("\\.");
            if (chunks.length < 2) return null;
            String headerJson = new String(
                    Base64.getUrlDecoder().decode(chunks[0].getBytes(StandardCharsets.UTF_8)));
            JsonNode headerNode = new ObjectMapper().readTree(headerJson);
            JsonNode kidNode = headerNode.get("kid");
            if (kidNode == null || kidNode.asText().isBlank()) return null;
            return kidNode.asText();
        } catch (Exception e) {
            return null;
    }
}
*/
