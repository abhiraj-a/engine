package com.Engine.Repository;

import com.Engine.Entity.ClusterNode;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ClusterNodeRepository extends ReactiveCrudRepository<ClusterNode, String> {

    // Automatically finds nodes that have pinged within the last 30 seconds
    @Query("SELECT * FROM cluster_node WHERE last_seen >= NOW() - INTERVAL '30 seconds'")
    Flux<ClusterNode> findActiveNodes();

    // Custom upsert query since R2DBC doesn't natively support "save()" for pre-assigned String IDs easily
    @Modifying
    @Query("INSERT INTO cluster_node (instance_id, url, last_seen) " +
            "VALUES (:instanceId, :url, NOW()) " +
            "ON CONFLICT (instance_id) DO UPDATE SET url = :url, last_seen = NOW()")
    Mono<Void> upsertHeartbeat(String instanceId, String url);
}
