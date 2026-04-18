package com.Engine.Filter;
import com.Engine.Utils.AuthifyerKeyProvider;
import com.Engine.Utils.Principal;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
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
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Collections;

@RequiredArgsConstructor
@Component
@Slf4j
public class AuthifyerFilter implements WebFilter {

private final AuthifyerKeyProvider provider;
@Value(("${frontend.url}"))
private String frontendUrl;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // checking origin from where the request is coming
        String origin = exchange.getRequest().getHeaders().getFirst("Origin");
        if (origin == null || !origin.equals(frontendUrl)) {
            return chain.filter(exchange);
        }

        log.warn("Dashboard request intercepted: {}", exchange.getRequest().getURI());


        String path = exchange.getRequest().getPath().toString();
        if (path.startsWith("/user/register")) {
            //do not filter this path
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isBlank()) {
            log.warn("Incoming authHeader :  "+ authHeader);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
          //  log.warn("Try block  initiated");
            String token = authHeader.substring(7);
            String[] chunks = token.split("\\.");
            ObjectMapper mapper = new ObjectMapper();
            String header = new String(Base64.getUrlDecoder().decode(chunks[0].getBytes(StandardCharsets.UTF_8)));
            JsonNode node = mapper.readTree(header);
            String kid = node.get("kid").asText();

            return provider.getPublicKey(kid)
                    .flatMap(publicKey -> {
                        try {

//                  log.warn("before alo");
                            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) publicKey, null);
                            JWTVerifier verifier = JWT.require(algorithm)
                                    .withIssuer("https://authifyer-backend.onrender.com")
                                    .acceptLeeway(5)
                                    .build();

                            DecodedJWT decodedJWT = verifier.verify(token);
                            String sub = decodedJWT.getClaim("sub").asString();
                            String email = decodedJWT.getClaim("email").asString();
                            Principal principal = new Principal(sub, email);

                            UsernamePasswordAuthenticationToken authenticationToken =
                                    new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

                            log.warn("Setting security context :  "  + authenticationToken);
                            return chain.filter(exchange)
                                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authenticationToken));
                        } catch (Exception e) {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            log.warn(e.getMessage());
                            return exchange.getResponse().setComplete();
                        }
                    })
                    .onErrorResume(e -> {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        log.warn(e.getMessage());
                        return exchange.getResponse().setComplete();
                    });

        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            log.warn(e.getMessage());
            return exchange.getResponse().setComplete();
        }
    }

}
