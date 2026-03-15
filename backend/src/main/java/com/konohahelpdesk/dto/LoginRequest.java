package com.konohahelpdesk.dto;

public class LoginRequest {

    private String email;
    private String username;
    private String password;


    // Getters — lets AuthController read the values
    // request.getEmail() and request.getPassword() won't work without these
    public String getEmail() {
        return email;
    }
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    // Setters — lets Spring automatically fill this object from the JSON body
    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
