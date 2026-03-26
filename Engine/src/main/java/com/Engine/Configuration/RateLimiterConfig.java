package com.Engine.Configuration;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver(){
        return exchange ->
            Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("X-Client-Id")).defaultIfEmpty("anonymous");
    }
}
