package com.Engine.Filter;

import com.Engine.Service.ManualCircuitBreaker;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ManualCircuitBreakerGatewayFilterFactory extends AbstractGatewayFilterFactory<ManualCircuitBreakerGatewayFilterFactory.Config>{
    private final Map<String, ManualCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    @Data
    public static class Config{
        private int failureThreshold = 5;
        private long recoveryTimeoutSeconds = 10;
    }

    public ManualCircuitBreakerGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange,chain)->{
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = route==null?"unknown-route": route.getId();

            ManualCircuitBreaker cb = circuitBreakers.computeIfAbsent(routeId,
                    id->new ManualCircuitBreaker(config.getFailureThreshold(),config.getRecoveryTimeoutSeconds()));
            if(!cb.isAllowed()){
                exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

                String fallbackJson = "{\"error\": \"Service Unavailable\", \"message\": \"Circuit Breaker is OPEN. Downstream service is failing.\"}";
                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(fallbackJson.getBytes(StandardCharsets.UTF_8));
                return exchange.getResponse().writeWith(Mono.just(buffer));
            }
            return chain.filter(exchange)
                    .doOnSuccess(v->{
                        HttpStatusCode status = exchange.getResponse().getStatusCode();
                        if(status!=null&&status.is5xxServerError()){
                            cb.recordFailure();
                        }
                        else {
                            cb.recordSuccess();
                        }
                    })
                    .doOnError(throwable -> {
                                log.warn("Network error to downstream on route {}: {}", routeId, throwable.getMessage());
                                cb.recordFailure();
                            }
                    );
        };
    }

}
