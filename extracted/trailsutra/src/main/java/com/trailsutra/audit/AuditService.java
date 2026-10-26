package com.trailsutra.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuditService {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper;
    public AuditService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    @Transactional public synchronized String append(String actor, String action, String detail) {
        try {
            Map<String,Object> event = new LinkedHashMap<>(); event.put("actor", actor); event.put("action", action); event.put("detail", detail); event.put("at", Instant.now().toString());
            String json = mapper.writeValueAsString(event);
            String previous = jdbc.query("SELECT event_hash FROM audit_log ORDER BY id DESC LIMIT 1", rs -> rs.next() ? rs.getString(1) : "0".repeat(64));
            String hash = sha256(previous + json);
            jdbc.update("INSERT INTO audit_log(event_json,previous_hash,event_hash,created_at) VALUES(?,?,?,?)", json, previous, hash, Instant.now()); return hash;
        } catch (Exception e) { throw new IllegalStateException("Audit append failed", e); }
    }
    public boolean verifyChain() {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT event_json,previous_hash,event_hash FROM audit_log ORDER BY id"); String previous = "0".repeat(64);
        for (Map<String,Object> row : rows) { String event = row.get("EVENT_JSON").toString(); String prev = row.get("PREVIOUS_HASH").toString(); String hash = row.get("EVENT_HASH").toString(); if (!previous.equals(prev) || !sha256(prev + event).equals(hash)) return false; previous = hash; }
        return true;
    }
    private String sha256(String text) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
