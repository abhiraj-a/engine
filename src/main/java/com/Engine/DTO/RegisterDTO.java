package com.Engine.DTO;

import lombok.Data;
import lombok.Getter;

@Getter
@Data
public class RegisterDTO {
    private String authifyerId;
    private String name;
    private String email;
}
