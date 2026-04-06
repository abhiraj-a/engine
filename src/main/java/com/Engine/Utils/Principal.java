package com.Engine.Utils;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Principal {
    private String sub;
    private String email;

    public String getEmail() {
        return email;
    }

    public String getSub() {
        return sub;
    }
}
