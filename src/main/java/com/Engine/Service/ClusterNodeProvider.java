package com.Engine.Service;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Component
@Slf4j
@Getter
@RequiredArgsConstructor
public class ClusterNodeProvider {

    private String thisServerInstance;
    private final HashRouter hashRouter;
    @Autowired
    private  DatabaseClient client;
    @Value("${RENDER_INSTANCE_ID:12345}") private String instanceId;
    @Value("${PORT:8080}") private String port;


    @PostConstruct
    public void init(){
        try {
            String internalIp = InetAddress.getLocalHost().getHostAddress();
            this.thisServerInstance = "http://" + internalIp + ":" + port;
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Scheduled(fixedRate = 10000)
    public void writeToDb(){
        String sql = "INSERT INTO cluster_node (instance_id , url) "+
                "VALUES (:id , :url) "+
                "ON CONFLICT (instance_id) DO UPDATE SET url = :url";

        client.sql(sql)
                .bind("id",instanceId)
                .bind("url", thisServerInstance)
                .then()
                .subscribe(
                        null,
                        error -> log.error("Failed to send cluster heartbeat", error)
                );
    }

    @Scheduled(fixedRate = 15000)
    public void updateHashRing() {
        // Only fetch nodes that have pinged us in the last 30 seconds
        client.sql("SELECT url FROM cluster_node WHERE last_seen >= NOW() - INTERVAL '30 seconds'")
                .map(row -> row.get("url", String.class))
                .all()
                .collectList()
                .subscribe(activeUrls -> {
                    if (activeUrls != null && !activeUrls.isEmpty()) {
                        hashRouter.updateCluster(activeUrls);
                    }
                });
    }
}
