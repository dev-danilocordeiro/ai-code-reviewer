package com.example.accounts;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountAdminController {

    private final AccountService accounts;

    public AccountAdminController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public AccountDto get(@PathVariable long id) {
        return accounts.get(id);
    }

    @PutMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> suspend(@PathVariable long id) {
        accounts.suspend(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        accounts.delete(id);
        return ResponseEntity.noContent().build();
    }
}
