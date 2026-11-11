package com.example.signup;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService {

    @Autowired
    private UserRepository users;

    @Autowired
    private WelcomeMailer mailer;

    public void signup(String email, String name) {
        createUser(email, name);
        mailer.sendWelcome(email);
    }

    @Transactional
    private void createUser(String email, String name) {
        users.save(new User(email, name));
        users.save(new AuditEntry("signup", email));
    }
}
