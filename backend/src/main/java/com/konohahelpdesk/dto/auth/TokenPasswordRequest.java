package com.konohahelpdesk.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for /activate and /reset-password: the emailed token + the user's chosen password. */
public class TokenPasswordRequest {

    @NotBlank @Size(max = 128)
    private String token;

    @NotBlank @Size(max = 128)
    private String password;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
