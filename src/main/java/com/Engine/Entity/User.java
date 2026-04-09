package com.Engine.Entity;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table
@Builder
public class User {
    @Id
    private UUID id;
    private String authifyerId;
    private String email;
}
