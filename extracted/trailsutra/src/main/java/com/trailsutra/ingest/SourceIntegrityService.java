package com.trailsutra.ingest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class SourceIntegrityService {
    private final JdbcTemplate jdbc;
    public SourceIntegrityService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public String record(MultipartFile file,String purpose,String authorityReference,String actor){if(purpose==null||purpose.isBlank()||authorityReference==null||authorityReference.isBlank())throw new IllegalArgumentException("Purpose and authority reference are required");try{String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));jdbc.update("INSERT INTO source_imports(source_name,source_hash,purpose,authority_reference,imported_by,imported_at) VALUES(?,?,?,?,?,?)",file.getOriginalFilename(),hash,purpose,authorityReference,actor,Instant.now());return hash;}catch(Exception e){throw new IllegalStateException("Source integrity recording failed",e);}}
}
