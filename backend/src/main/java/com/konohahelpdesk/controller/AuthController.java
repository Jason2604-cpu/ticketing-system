package com.konohahelpdesk.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import com.konohahelpdesk.dto.LoginRequest;      
import com.konohahelpdesk.service.AuthService;
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(
        @RequestBody LoginRequest request,
        HttpServletResponse response   // lets us attach a cookie
    ) {
        // AuthService validates credentials and returns JWT string
        String token = authService.login(request.getEmail(), request.getPassword());

        // Create the HttpOnly cookie — JS cannot read this
        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);    // JS cannot access it
        cookie.setSecure(true);      // HTTPS only (set false for localhost dev)
        cookie.setPath("/");         // sent on every request to your backend
        cookie.setMaxAge(86400);     // expires in 24 hours (seconds)

        // Attach cookie to the response — browser stores it automatically
        response.addCookie(cookie);

        return ResponseEntity.ok().body("Login successful");
        // Notice: we do NOT send the token in the response body anymore
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {

        // To logout — overwrite the cookie with an expired one
        Cookie cookie = new Cookie("jwt", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);   // 0 = delete immediately

        response.addCookie(cookie);

        return ResponseEntity.ok().body("Logged out");
    }
}
