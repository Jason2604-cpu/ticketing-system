package com.konohahelpdesk.dto.auth;

import com.konohahelpdesk.controller.auth.AuthController;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 254)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(max = 128)
    private String password;

    // Getters — lets AuthController read the values
    public String getEmail() {
        return email;
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
