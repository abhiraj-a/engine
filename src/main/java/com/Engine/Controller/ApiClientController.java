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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/admin/clients")
@RequiredArgsConstructor
@Slf4j
public class ApiClientController {

    private final InMemoryRateLimitService rateLimitService;
    private final ApiClientRepository apiClientRepository;
    private final LiveMetricsTracker metricsTracker;

    @PostMapping("/register-new/service")
    public Mono<ApiClientRespone> registerNew(@RequestBody ApiClientDTO apiClientDTO) {
        ApiClient apiClient = ApiClient.builder()
                .clientName(apiClientDTO.getClientName())
                .jwksUrl((apiClientDTO.getJwksUrl() != null && !apiClientDTO.getJwksUrl().isBlank()) ? apiClientDTO.getJwksUrl() : null)
                .currentTokens(100)
                .clientId(IdGenerator.generateClientId())
                .rateLimitCapacity(100)
                .rateLimitRefill(5)
                .isSuspended(false)
                .lastRefillTime(Instant.now())
                .build();

        return apiClientRepository.save(apiClient)
                .map(saved -> ApiClientRespone.builder()
                        .jwksUrl(saved.getJwksUrl() != null ? saved.getJwksUrl() : "")
                        .clientId(saved.getClientId())
                        .clientName(saved.getClientName())
                        .currentTokens(saved.getCurrentTokens())
                        .build());
    }

    @GetMapping("/get-all")
    public Flux<ApiClientRespone> getAllClients() {
        return apiClientRepository.findAll()
                .map(a -> ApiClientRespone.builder()
                        .clientId(a.getClientId())
                        .clientName(a.getClientName())
                        .currentTokens(a.getCurrentTokens())
                        .isSuspended(a.isSuspended())
                        .build());
    }

    @GetMapping(value = "/metrics/stream/{clientId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<MetricDTO> getMetrics(@PathVariable String clientId) {
        return apiClientRepository.findByClientId(clientId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Client not found")))
                .flatMapMany(client -> Flux.interval(Duration.ofSeconds(1))
                        .flatMap(tick -> rateLimitService.getLiveTokens(clientId)
                                .map(tokens -> {
                                    ClientMetrics metrics = metricsTracker.getClientMetrics(clientId);
                                    return MetricDTO.builder()
                                            .liveTokens(tokens)
                                            .totalRequests(metrics.getTotRequest())
                                            .passedRequests(metrics.getPassedRequest())
                                            .blockedRequests(metrics.getBlockedRequest())
                                            .build();
                                })
                        )
                );
    }
}

