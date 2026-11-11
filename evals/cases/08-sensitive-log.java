package com.example.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final CredentialStore store;

    public LoginService(CredentialStore store) {
        this.store = store;
    }

    public boolean login(String username, String password) {
        log.info("Login attempt user={} password={}", username, password);
        boolean ok = store.verify(username, password);
        if (!ok) {
            log.warn("Failed login for {}", username);
        }
        return ok;
    }
}
