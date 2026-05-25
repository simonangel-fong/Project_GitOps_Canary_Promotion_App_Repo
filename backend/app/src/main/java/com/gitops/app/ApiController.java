package com.gitops.app;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    private final AppProperties props;
    private final PgdbHealthIndicator pgdbHealth;

    public ApiController(AppProperties props, PgdbHealthIndicator pgdbHealth) {
        this.props = props;
        this.pgdbHealth = pgdbHealth;
    }

    @GetMapping("/api")
    public Map<String, Object> root() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("version", props.getBackendVersion());
        response.put("bg_color", props.getFrontendBgColor());
        return response;
    }

    @GetMapping("/api/healthz")
    public ResponseEntity<Map<String, Object>> healthz() {
        Map<String, Object> response = new LinkedHashMap<>();
        if (!pgdbHealth.isHealthy()) {
            response.put("status", "db_error");
            return ResponseEntity.status(503).body(response);
        }
        response.put("status", "ok");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/env")
    public Map<String, Object> env() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("FRONTEND_BG_COLOR", props.getFrontendBgColor());
        response.put("BACKEND_VERSION", props.getBackendVersion());
        response.put("PGDB_ENABLE", props.isPgdbEnable());
        response.put("PGDB_URL", props.getPgdbUrl());
        response.put("OOM_ENABLE", props.isOomEnable());
        response.put("OOM_TIME", props.getOomTime());
        return response;
    }
}
