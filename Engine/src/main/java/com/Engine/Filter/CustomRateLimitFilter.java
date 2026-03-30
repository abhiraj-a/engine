package com.Engine.Filter;

import com.Engine.Service.InMemoryRateLimitService;
import com.Engine.Repository.ApiClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CustomRateLimitFilter implements GlobalFilter, Ordered {

    private final ApiClientRepository apiClientRepository;
    private final InMemoryRateLimitService rateLimitService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientId = exchange.getRequest().getHeaders().getFirst("X-Client-Id");
        return rateLimitService.isAllowed(clientId)
                .flatMap(allowed->{
                    if((boolean) allowed){
                      return chain.filter(exchange);
                    }
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return exchange.getResponse().setComplete();
                });
    }

    @Override
    public int getOrder() {
        return -10;
    }
}
