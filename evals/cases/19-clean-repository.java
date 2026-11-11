package com.example.users;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserLookupDao {

    private final JdbcClient jdbc;

    public UserLookupDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findByEmail(String email) {
        return jdbc.sql("SELECT id, name, email FROM users WHERE email = :email")
                .param("email", email)
                .query(UserRow.class)
                .optional();
    }

    public record UserRow(long id, String name, String email) {
    }
}
