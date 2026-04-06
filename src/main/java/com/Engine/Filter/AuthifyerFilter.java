package com.Engine.Filter;
import com.Engine.Utils.AuthifyerKeyProvider;
import com.Engine.Utils.Principal;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
public class AuthifyerFilter implements WebFilter {



private final AuthifyerKeyProvider provider;
@Value(("${frontend.url}"))
private String frontendUrl;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String host = exchange.getRequest().getHeaders().getOrigin();
        if(host==null||!host.startsWith(frontendUrl)){
            return chain.filter(exchange);
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if(authHeader==null||authHeader.isBlank()){
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        try {
            String token = authHeader.substring(7);
            String[] chunks = token.split("\\.");
            ObjectMapper mapper = new ObjectMapper();
            String header = new String(Base64.getUrlDecoder().decode(chunks[0].getBytes(StandardCharsets.UTF_8)));
            JsonNode  node = mapper.readTree(header);
            RSAPublicKey publicKey = null;
            publicKey = (RSAPublicKey) provider.getPublicKey(node.get("kid").asText());
            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            JWTVerifier  verifier = JWT.require(Algorithm.RSA256((RSAKeyProvider) provider.getPublicKey(node.get("kid").asText())))
                        .withIssuer("https://authifyer-backend.onrender.com")
                        .build();
            DecodedJWT decodedJWT = verifier.verify(token);
            String sub = String.valueOf(decodedJWT.getClaim("sub"));
            String email = String.valueOf(decodedJWT.getClaim("email"));
            Principal principal = new Principal(sub, email);
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
            ReactiveSecurityContextHolder.withAuthentication(authenticationToken);
        }
        catch (Exception e){
            throw new RuntimeException(e);
        }
        return chain.filter(exchange);

    }

}
