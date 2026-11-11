package com.example.session;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Base64;

@RestController
public class SessionImportController {

    @PostMapping("/api/session/import")
    public String importSession(@RequestBody String encoded) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object session = in.readObject();
            return "imported " + session.getClass().getSimpleName();
        }
    }
}
