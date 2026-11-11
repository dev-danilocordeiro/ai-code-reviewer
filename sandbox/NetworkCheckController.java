package com.example.diagnostics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class NetworkCheckController {

    @GetMapping("/diagnostics/ping")
    public String ping(@RequestParam String host) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[] {"sh", "-c", "ping -c 1 " + host});
        int exit = process.waitFor();
        return exit == 0 ? "reachable" : "unreachable";
    }
}
