package com.Engine.Service;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Component
public class ClusterNodeProvider {

    @Getter
    @Value("${engine.cluster.nodes}")
    private List<String> activeNodes;

    @Getter
    private String thisServerInstance;
    @PostConstruct
    public void init(){
        try {
            String thisServerIp = InetAddress.getLocalHost().getHostAddress();
            this.thisServerInstance = "http://"+thisServerIp;
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

}
