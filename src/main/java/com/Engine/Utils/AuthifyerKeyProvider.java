package com.Engine.Utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
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


    public PublicKey getPublicKey(String kid) throws MalformedURLException, JsonProcessingException {
        if(cache.containsKey(kid) &&System.currentTimeMillis() - lastFetchTime < ttl){
            return cache.get(kid);
        }
        getKey();
        return cache.get(kid);
    }
    private void getKey() throws MalformedURLException, JsonProcessingException {

        WebClient client = WebClient.builder()
                .baseUrl("https://authifyer-backend.onrender.com")
                .build();

      String jwks = client.get()
               .uri(URI.create("/authifyer/.well-known/jwks.json"))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        ObjectMapper mapper =new ObjectMapper();
        JsonNode node = mapper.readTree(jwks);
        JsonNode keys= node.get("keys");
        if(keys!=null){
            for (var key : keys){
                String kid  = key.get("kid").asText();
                String n = key.get("n").asText();
                String e = key.get("e").asText();
                try {
                    PublicKey publicKey = createPublicKey(n,e);
                    cache.put(kid,publicKey);
                } catch (InvalidKeySpecException ex) {
                    throw new RuntimeException(ex);
                } catch (NoSuchAlgorithmException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        lastFetchTime= System.currentTimeMillis();
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
