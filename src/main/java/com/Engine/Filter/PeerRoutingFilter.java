package com.Engine.Filter;
import com.Engine.Service.ClusterNodeProvider;
import com.Engine.Service.HashRouter;
import jakarta.annotation.PostConstruct;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.net.URI;

@Component
public class PeerRoutingFilter implements GlobalFilter, Ordered {

    private final ClusterNodeProvider clusterNodeProvider;
    private final HashRouter hashRouter;

    public PeerRoutingFilter(ClusterNodeProvider clusterNodeProvider, HashRouter hashRouter) {
        this.clusterNodeProvider = clusterNodeProvider;
        this.hashRouter = hashRouter;
    }

    @PostConstruct
    public void init(){
        hashRouter.updateCluster(clusterNodeProvider.getActiveNodes());
    }


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getHeaders().containsKey("X-Engine-Peer-Routed")){
            return chain.filter(exchange);
        }
        String clientId = exchange.getRequest().getHeaders().getFirst("X-Client-Id");
        if(clientId==null||clientId.isBlank()){
            return chain.filter(exchange);
        }
        String ownerNode = hashRouter.getOwnerNode(clientId);
        String thisNodeId = clusterNodeProvider.getThisServerInstance();
        if(ownerNode.isBlank()){
            return  chain.filter(exchange);
        }
        if(ownerNode.equals(thisNodeId)){
            return chain.filter(exchange);
        }
        URI targetUri = URI.create(ownerNode+exchange.getRequest().getURI().getPath());
        ServerWebExchange mutated= exchange.mutate()
                .request(r->r.header("X-Engine-Peer-Routed","true"))
                .build();
        mutated.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR,targetUri);
        mutated.getAttributes().put(ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR,"http");
        mutated.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR,"true");
        return chain.filter(mutated);
    }


    @Override
    public int getOrder() {
        return -20;
    }
}
