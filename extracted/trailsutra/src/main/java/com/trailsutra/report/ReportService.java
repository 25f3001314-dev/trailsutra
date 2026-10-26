package com.trailsutra.report;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.trailsutra.audit.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ReportService {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper; private final AuditService audit; private final KeyPair keyPair;
    public ReportService(JdbcTemplate jdbc,ObjectMapper mapper,AuditService audit){this.jdbc=jdbc;this.mapper=mapper;this.audit=audit;try{KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);keyPair=g.generateKeyPair();}catch(Exception e){throw new IllegalStateException(e);}}
    public byte[] createPdf(String caseId){
        Map<String,Object> c=jdbc.queryForMap("SELECT ca.case_id,ca.status,ca.officer,ca.created_at,co.victim_name,co.amount_paise,co.complaint_ref FROM cases ca JOIN complaints co ON co.id=ca.complaint_id WHERE ca.case_id=?",caseId);
        ByteArrayOutputStream out=new ByteArrayOutputStream();Document doc=new Document();
        try{PdfWriter.getInstance(doc,out);doc.open();doc.add(new Paragraph("TrailSutra | Investigation Support Report"));doc.add(new Paragraph("SYNTHETIC DATA — review required"));doc.add(new Paragraph("Case ID: "+caseId));doc.add(new Paragraph("Status: "+c.get("STATUS")));doc.add(new Paragraph("Officer: "+c.get("OFFICER")));doc.add(new Paragraph("Victim: "+c.get("VICTIM_NAME")));doc.add(new Paragraph("Complaint reference: "+c.get("COMPLAINT_REF")));doc.add(new Paragraph("Complaint amount: ₹"+(((Number)c.get("AMOUNT_PAISE")).longValue()/100.0)));doc.add(new Paragraph("Created: "+c.get("CREATED_AT")));doc.add(new Paragraph(""));doc.add(new Paragraph("Time-ordered observed transactions"));
            jdbc.query("SELECT t.txn_id,t.posted_at,t.amount_paise,s.display_name src,d.display_name dst FROM transactions t LEFT JOIN accounts s ON s.token=t.src_token LEFT JOIN accounts d ON d.token=t.dst_token WHERE t.txn_id IN ('TX-SUNITA-01','TX-A-B','TX-A-C','TX-A-D','TX-B-E','TX-C-E','TX-D-R') ORDER BY t.posted_at",rs->{while(rs.next())try{doc.add(new Paragraph(rs.getString("TXN_ID")+" | "+rs.getTimestamp("POSTED_AT")+" | "+rs.getString("SRC")+" -> "+rs.getString("DST")+" | ₹"+(rs.getLong("AMOUNT_PAISE")/100.0)+" | Observed"));}catch(Exception e){throw new IllegalStateException(e);}return null;});
            doc.add(new Paragraph(""));doc.add(new Paragraph("Account leads require human review. No automatic account action is performed."));doc.add(new Paragraph("Unobserved: onward movement beyond available records is not asserted."));doc.close();
            byte[] pdf=out.toByteArray();String hash=sha(pdf);Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("caseId",caseId);manifest.put("fileHash",hash);manifest.put("createdAt",Instant.now().toString());manifest.put("label","signed manifest");String json=mapper.writeValueAsString(manifest);Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(keyPair.getPrivate());signature.update(json.getBytes(StandardCharsets.UTF_8));String sig=java.util.Base64.getEncoder().encodeToString(signature.sign());jdbc.update("INSERT INTO report_manifests(case_id,file_name,file_hash,manifest_json,signature,created_at) VALUES(?,?,?,?,?,?)",caseId,caseId+".pdf",hash,json,sig,Instant.now());audit.append("system","REPORT_CREATED",caseId+"|"+hash);return pdf;
        }catch(Exception e){if(doc.isOpen())doc.close();throw new IllegalStateException("Report generation failed",e);}
    }
    public Map<String,Object> verify(String caseId,byte[] current){Map<String,Object> m=jdbc.queryForMap("SELECT file_hash,manifest_json,signature FROM report_manifests WHERE case_id=? ORDER BY id DESC LIMIT 1",caseId);String actual=sha(current);boolean hashMatches=actual.equals(m.get("FILE_HASH").toString());boolean signatureValid=false;try{Signature s=Signature.getInstance("SHA256withRSA");s.initVerify(keyPair.getPublic());s.update(m.get("MANIFEST_JSON").toString().getBytes(StandardCharsets.UTF_8));signatureValid=s.verify(java.util.Base64.getDecoder().decode(m.get("SIGNATURE").toString()));}catch(Exception ignored){}return Map.of("hashMatches",hashMatches,"signatureValid",signatureValid,"storedHash",m.get("FILE_HASH"),"currentHash",actual,"status",hashMatches&&signatureValid?"Verified":"Mismatch detected");}
    private String sha(byte[] data){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}catch(Exception e){throw new IllegalStateException(e);}}
}
