package com.example.users;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class UserSearchDao {

    private final JdbcTemplate jdbc;

    public UserSearchDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> findByName(String name) {
        String sql = "SELECT id, name, email FROM users WHERE name = '" + name + "'";
        return jdbc.queryForList(sql);
    }
}
