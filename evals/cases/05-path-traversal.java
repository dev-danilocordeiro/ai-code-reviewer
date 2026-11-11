package com.example.files;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
public class ReportDownloadController {

    private static final Path REPORTS_DIR = Path.of("/var/app/reports");

    @GetMapping("/reports/{fileName}")
    public Resource download(@PathVariable String fileName) {
        Path file = REPORTS_DIR.resolve(fileName);
        return new PathResource(file);
    }
}
