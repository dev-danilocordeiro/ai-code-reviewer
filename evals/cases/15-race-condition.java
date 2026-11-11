package com.example.ratelimit;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RequestCounter {

    private final Map<String, Integer> counts = new HashMap<>();

    public boolean allow(String clientId, int limit) {
        Integer current = counts.get(clientId);
        if (current == null) {
            current = 0;
        }
        if (current >= limit) {
            return false;
        }
        counts.put(clientId, current + 1);
        return true;
    }
}
