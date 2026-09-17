package com.liuqitech.accountingassistant.dto;

public class AuthResponse {
    private String username;

    public AuthResponse() {
    }

    public AuthResponse(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
