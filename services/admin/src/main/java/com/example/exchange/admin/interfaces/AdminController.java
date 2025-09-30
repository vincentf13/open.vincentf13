package com.example.exchange.admin.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative endpoints for toggling risk management features.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/heartbeat")
    public ResponseEntity<String> heartbeat() {
        return ResponseEntity.ok("admin-ok");
    }
}
