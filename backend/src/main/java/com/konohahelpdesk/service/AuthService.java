package com.konohahelpdesk.service;
import org.springframework.stereotype.Service;

@Service

public class AuthService {

    public String login(String email, String password) {

        // Temporary placeholder — returns a fake token for now
        // We will replace this with real logic soon
        return "fake-jwt-token";
    }
}
