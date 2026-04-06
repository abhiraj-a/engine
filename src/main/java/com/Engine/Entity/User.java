package com.Engine.Entity;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table
@Getter
@Builder
public class User {

    @Id
    private UUID id;
    private String authifyerId;
    private Instant createdAt;
    private String email;
    private String name;

}
