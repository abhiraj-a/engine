package com.Engine.Entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.util.UUID;

@Table("\"user\"")
@Builder
@Data
public class User {
    @Id
    private UUID id;
    private String authifyerId;
    private @Nullable  String email;
}
