package com.Engine.Controller;

import com.Engine.DTO.ApiClientDTO;
import com.Engine.DTO.ApiClientRespone;
import com.Engine.DTO.MetricDTO;
import com.Engine.Entity.ApiClient;
import com.Engine.Repository.ApiClientRepository;
import com.Engine.Service.ClientMetrics;
import com.Engine.Service.InMemoryRateLimitService;
import com.Engine.Service.LiveMetricsTracker;
import com.Engine.Utils.IdGenerator;
import com.Engine.Utils.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/admin/clients")
@RequiredArgsConstructor
@Slf4j
public class ApiClientController {

    private final InMemoryRateLimitService rateLimitService;
    private final ApiClientRepository apiClientRepository;
    private final LiveMetricsTracker metricsTracker;
    @PostMapping("/register-new/service")
    public Mono<?> registerNew(@AuthenticationPrincipal Principal principal, @RequestBody ApiClientDTO apiClientDTO){
        ApiClient apiClient = ApiClient.builder()
                .clientName(apiClientDTO.getClientName())
                .authifyerId(principal.getSub())
                .jwksUrl(!apiClientDTO.getJwksUrl().isBlank()? apiClientDTO.getJwksUrl() : null)
                .currentTokens(100)
                .clientId(IdGenerator.generateClientId())
                .rateLimitCapacity(100)
                .rateLimitRefill(5)
                .isSuspended(false)
                .build();
//        ApiClientRespone respone = ApiClientRespone.builder()
//                .jwksUrl(apiClient.getJwksUrl()!=null? apiClient.getJwksUrl() : "")
//                .clientId(apiClient.getClientId())
//                .clientName(apiClient.getClientName())
//                .authifyerId(apiClient.getAuthifyerId())
//                .currentTokens(apiClient.getCurrentTokens())
//                .build();
        return Mono.just(apiClientRepository.save(apiClient))
                .map(a->ApiClientRespone.builder()
                        .jwksUrl(apiClient.getJwksUrl()!=null? apiClient.getJwksUrl() : "")
                        .clientId(apiClient.getClientId())
                        .clientName(apiClient.getClientName())
                        .authifyerId(apiClient.getAuthifyerId())
                        .currentTokens(apiClient.getCurrentTokens())
                        .build());
    }

    @GetMapping("/get-all")
    public Flux<?> getAllClients(@AuthenticationPrincipal Principal principal){
        log.warn("Authyfyer if id : " + principal.getSub());
        return apiClientRepository.findAllByAuthifyerId(principal.getSub())
                .doOnNext(a -> log.warn("Found client: {}", a.getClientId()))
                .map(a -> ApiClientRespone.builder()
                        .clientId(a.getClientId())
                        .clientName(a.getClientName())
                        .currentTokens(a.getCurrentTokens())
                        .isSuspended(a.isSuspended())
                        .authifyerId(a.getAuthifyerId())
                        .build());

    }

    @GetMapping(value = "/tokens/stream/{clientId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Double> getLiveTokens(@AuthenticationPrincipal Principal principal,
                                      @PathVariable String clientId){
        return apiClientRepository.findByClientId(clientId)
                .filter(a->a.getAuthifyerId().equals(principal.getSub()))
//              .switchIfEmpty(Mono.error(new SecurityException("Unauthorized access to client metrics")))
                .flatMapMany(c->Flux.interval(Duration.ofSeconds(1)))
                .flatMap(tick-> rateLimitService.getLiveTokens(clientId));
    }

    @GetMapping("/metrics/stream/{clientId}")
    public Flux<?> getMetrics(@AuthenticationPrincipal Principal principal , @PathVariable String clientId){
//        return apiClientRepository.findByClientId(clientId)
//                .filter(a -> a.getAuthifyerId().equals(principal.getSub()))
//                .flatMapMany(c -> Flux.interval(Duration.ofSeconds(1)))
//                .flatMap(tick -> {
//                    ClientMetrics metrics = metricsTracker.getClientMetrics(clientId);
//                    return rateLimitService.getLiveTokens(clientId)
//                            .map(tokens -> MetricDTO.builder()
//                                    .liveTokens(tokens)
//                                    .totalRequests(metrics.getTotRequest())
//                                    .passedRequests(metrics.getPassedRequest())
//                                    .blockedRequests(metrics.getBlockedRequest())
//                                    .build());
//                });
        return apiClientRepository.findByClientId(clientId)
                .filter(a -> a.getAuthifyerId().equals(principal.getSub()))
                .flatMapMany(client -> Flux.interval(Duration.ofSeconds(1))
                        .map(tick -> {
                            double elapsed = (System.currentTimeMillis() - client.getLastRefillTime().toEpochMilli()) / 1000.0;
                            double liveTokens = Math.min(
                                    client.getRateLimitCapacity(),
                                    client.getCurrentTokens() + (client.getRateLimitRefill() * elapsed)
                            );
                            ClientMetrics metrics = metricsTracker.getClientMetrics(clientId);
                            return MetricDTO.builder()
                                    .liveTokens(liveTokens)
                                    .totalRequests(metrics.getTotRequest())
                                    .passedRequests(metrics.getPassedRequest())
                                    .blockedRequests(metrics.getBlockedRequest())
                                    .build();
                        })
                );
    }
}
