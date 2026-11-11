package com.example.preview;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class LinkPreviewController {

    private final RestClient http = RestClient.create();

    @GetMapping("/api/preview")
    public ResponseEntity<String> preview(@RequestParam String url) {
        String html = http.get().uri(url).retrieve().body(String.class);
        String title = extractTitle(html);
        return ResponseEntity.ok(title);
    }

    private String extractTitle(String html) {
        int start = html.indexOf("<title>");
        int end = html.indexOf("</title>");
        return start >= 0 && end > start ? html.substring(start + 7, end) : "";
    }
}
