package com.Engine.Utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;


@Component
public class AuthifyerKeyProvider {

    Map<String , PublicKey> cache = new HashMap<>();
    private long ttl = 360000;
    private long lastFetchTime=0;


    public Mono<PublicKey> getPublicKey(String kid) throws MalformedURLException, JsonProcessingException {
        if(cache.containsKey(kid) &&System.currentTimeMillis() - lastFetchTime < ttl){
            return Mono.just(cache.get(kid));
        }
        return getKey().map(publickey->cache.get(kid));
    }
    private Mono<PublicKey> getKey() throws MalformedURLException, JsonProcessingException {

        WebClient client = WebClient.builder()
                .baseUrl("https://authifyer-backend.onrender.com")
                .build();

      return client.get()
               .uri("/authifyer/.well-known/jwks.json")
                .retrieve()
                .bodyToMono(String.class)
              .flatMap(Jwks -> {
                  try {
                      ObjectMapper mapper = new ObjectMapper();
                      JsonNode node = mapper.readTree(Jwks);
                      JsonNode keys = node.get("keys");
                      if (keys != null) {
                          for (var key : keys) {
                              String kid = key.get("kid").asText();
                              String n = key.get("n").asText();
                              String e = key.get("e").asText();
                              PublicKey publicKey = createPublicKey(n, e);
                              cache.put(kid, publicKey);
                          }
                      }
                      lastFetchTime = System.currentTimeMillis();
                      return Mono.empty();
                  } catch (Exception ex) {
                      return Mono.error(ex);
                  }
              });

    }

    private PublicKey createPublicKey(String n, String e) throws InvalidKeySpecException, NoSuchAlgorithmException {

        byte[] mod = Base64.getUrlDecoder().decode(n);
        byte[] ex = Base64.getUrlDecoder().decode(e);

        BigInteger modulus = new BigInteger(1, mod);
        BigInteger publicExponent = new BigInteger(1, ex);

        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, publicExponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }

}
