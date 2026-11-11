package com.example.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AuditLogController {

    private final AuditEventRepository repository;

    public AuditLogController(AuditEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/audit-events")
    public List<AuditEventDto> list() {
        return repository.findAll().stream()
                .map(AuditEventDto::from)
                .toList();
    }
}
