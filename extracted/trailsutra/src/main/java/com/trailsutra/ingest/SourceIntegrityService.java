package com.trailsutra.ingest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class SourceIntegrityService {
    private final JdbcTemplate jdbc;
    public SourceIntegrityService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public String record(MultipartFile file,String purpose,String authorityReference,String actor) {
        validate(purpose, authorityReference, actor);
        try {
            return record(file.getOriginalFilename(), file.getBytes(), purpose, authorityReference, actor);
        } catch (IOException e) {
            throw new IllegalStateException("Source integrity recording failed", e);
        }
    }

    public String record(Path file, String purpose, String authorityReference, String actor) {
        validate(purpose, authorityReference, actor);
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
            }
            return save(file.getFileName().toString(), HexFormat.of().formatHex(digest.digest()),
                    purpose, authorityReference, actor);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Source integrity recording failed for " + file, e);
        }
    }

    private String record(String name, byte[] content, String purpose, String authorityReference,
                          String actor) {
        try {
            String hash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
            return save(name, hash, purpose, authorityReference, actor);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Source integrity recording failed for " + name, e);
        }
    }

    private String save(String name, String hash, String purpose, String authorityReference,
                        String actor) {
        jdbc.update("INSERT INTO source_imports(source_name,source_hash,purpose,authority_reference,imported_by,imported_at) VALUES(?,?,?,?,?,?)",
                name, hash, purpose, authorityReference, actor, Instant.now());
        return hash;
    }

    private void validate(String purpose, String authorityReference, String actor) {
        if (purpose == null || purpose.isBlank() || authorityReference == null
                || authorityReference.isBlank() || actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Purpose, authority reference, and actor are required");
        }
    }
}
