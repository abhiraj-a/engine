package com.Engine.Entity;

import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

public class ClusterNode {
    @Id
    private String instanceId;
    private String url;
    private LocalDateTime lastSeen;
}
