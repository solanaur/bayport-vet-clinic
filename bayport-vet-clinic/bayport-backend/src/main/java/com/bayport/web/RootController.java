package com.bayport.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public root so opening the Render URL in a browser is not a bare 403
 * (API routes under /api/** still require auth except configured permitAll paths).
 */
@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        return ResponseEntity.ok(Map.of(
                "service", "Bayport API",
                "health", "/api/health"
        ));
    }
}
