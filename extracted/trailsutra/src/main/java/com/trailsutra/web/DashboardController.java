package com.trailsutra.web;

import com.trailsutra.audit.AuditService;
import com.trailsutra.explainer.PlainLanguageExplainer;
import com.trailsutra.matching.MatchingService;
import com.trailsutra.report.ReportService;
import com.trailsutra.tokenisation.TokenisationService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
public class DashboardController {
    private static final String CASE_ID = "TS-2026-000458";
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final TokenisationService tokens;
    private final ReportService reports;
    private final MatchingService matching;
    private final PlainLanguageExplainer explainer;

    public DashboardController(JdbcTemplate jdbc, AuditService audit, TokenisationService tokens,
                               ReportService reports, MatchingService matching,
                               PlainLanguageExplainer explainer) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.tokens = tokens;
        this.reports = reports;
        this.matching = matching;
        this.explainer = explainer;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/overview";
    }

    @GetMapping("/overview")
    public String overview(Model model, Authentication auth) {
        addLayout(model, auth, "overview");
        model.addAttribute("overview", jdbc.queryForMap("""
                SELECT ca.case_id, ca.status, ca.officer, c.victim_name, c.amount_paise,
                       (SELECT COUNT(*) FROM trail_edges e WHERE e.case_id=ca.case_id) observed_count,
                       (SELECT COUNT(*) FROM account_scores s WHERE s.case_id=ca.case_id) lead_count
                FROM cases ca JOIN complaints c ON c.id=ca.complaint_id WHERE ca.case_id=?
                """, CASE_ID));
        return "overview";
    }

    @GetMapping("/intake")
    public String intake(Model model, Authentication auth) {
        addLayout(model, auth, "intake");
        ComplaintData complaint = complaint(CASE_ID);
        model.addAttribute("caseId", CASE_ID);
        model.addAttribute("complaint", complaint);
        model.addAttribute("incidentAt", complaint.incidentAt().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
        return "intake";
    }

    @GetMapping("/trail")
    public String trail(Model model, Authentication auth) {
        addLayout(model, auth, "trail");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT e.txn_id, e.src_token, e.dst_token, e.amount_paise, e.event_at,
                       CASE WHEN EXISTS (SELECT 1 FROM source_imports si
                                         WHERE si.source_hash=t.source_hash
                                           AND si.purpose='Public benchmark data')
                            THEN 'Public benchmark data' END source_label,
                       s.display_name src_name, d.display_name dst_name,
                       COALESCE(ss.role, 'Suspected lead') src_role,
                       COALESCE(ds.role, 'Suspected lead') dst_role
                FROM trail_edges e
                JOIN transactions t ON t.txn_id=e.txn_id
                LEFT JOIN accounts s ON s.token=e.src_token
                LEFT JOIN accounts d ON d.token=e.dst_token
                LEFT JOIN account_scores ss ON ss.case_id=e.case_id AND ss.account_token=e.src_token
                LEFT JOIN account_scores ds ON ds.case_id=e.case_id AND ds.account_token=e.dst_token
                WHERE e.case_id=? ORDER BY e.event_at, e.hop
                """, CASE_ID);
        model.addAttribute("trail", rows);
        model.addAttribute("nodes", graphNodes(rows));
        model.addAttribute("edges", graphEdges(rows));
        return "trail";
    }

    @GetMapping("/triage")
    public String triage(Model model, Authentication auth) {
        addLayout(model, auth, "triage");
        List<Map<String, Object>> accounts = jdbc.queryForList("""
                SELECT a.token, a.display_name, a.masked_account, a.segment, a.balance_paise,
                       a.monthly_turnover_paise, s.priority_score risk, s.role, s.recommendation,
                       s.reasons, s.tainted_held_paise
                FROM accounts a
                JOIN account_scores s ON s.account_token=a.token AND s.case_id=?
                WHERE a.token <> (SELECT c.victim_token FROM cases ca
                                  JOIN complaints c ON c.id=ca.complaint_id WHERE ca.case_id=?)
                ORDER BY s.priority_score DESC
                """, CASE_ID, CASE_ID);
        for (Map<String, Object> account : accounts) {
            Object reasonValue = account.get("REASONS");
            if (reasonValue == null) {
                continue;
            }
            String reason = reasonValue.toString();
            String explained = explainer.explain(reason);
            if (!reason.equals(explained)) {
                account.put("explained_reasons", explained);
            }
        }
        model.addAttribute("accounts", accounts);
        return "triage";
    }

    @GetMapping("/review")
    public String review(Model model, Authentication auth) {
        addLayout(model, auth, "review");
        model.addAttribute("account", jdbc.queryForMap("""
                SELECT a.token, a.display_name, a.segment, a.masked_account, a.balance_paise,
                       a.monthly_turnover_paise, s.priority_score risk, s.role, s.reasons,
                       s.tainted_held_paise, DATEDIFF('DAY', a.opened_at, CURRENT_TIMESTAMP) account_age_days
                FROM accounts a JOIN account_scores s ON s.account_token=a.token AND s.case_id=?
                WHERE a.display_name='Ramesh Verma'
                """, CASE_ID));
        model.addAttribute("caseId", CASE_ID);
        return "review";
    }

    @GetMapping("/reports")
    public String reports(Model model, Authentication auth) {
        addLayout(model, auth, "reports");
        model.addAttribute("caseId", CASE_ID);
        model.addAttribute("auditRows", jdbc.queryForList(
                "SELECT id,created_at,event_json,event_hash FROM audit_log ORDER BY id DESC LIMIT 10"));
        return "reports";
    }

    @GetMapping("/settings")
    public String settings(Model model, Authentication auth) {
        addLayout(model, auth, "settings");
        return "settings";
    }

    @RequestMapping("/403")
    public String forbidden() {
        return "error/403";
    }

    @PostMapping("/api/cases/{caseId}/match")
    @ResponseBody
    public List<MatchCandidate> match(@PathVariable("caseId") String caseId,
                                      @RequestBody MatchRequest request) {
        if (request.ncrpId() == null || request.ncrpId().isBlank()
                || request.victimName() == null || request.victimName().isBlank()
                || request.amountPaise() <= 0 || request.incidentAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complaint details are incomplete");
        }
        ComplaintData caseComplaint = complaint(caseId);
        List<MatchCandidate> candidates = jdbc.query("""
                        SELECT t.txn_id, t.reference_token, t.src_token, t.dst_token,
                               t.posted_at, t.amount_paise, s.display_name src_name, d.display_name dst_name,
                               CASE WHEN EXISTS (SELECT 1 FROM source_imports si
                                                 WHERE si.source_hash=t.source_hash
                                                   AND si.purpose='Public benchmark data')
                                    THEN 'Public benchmark data' END source_label
                        FROM transactions t
                        LEFT JOIN accounts s ON s.token=t.src_token
                        LEFT JOIN accounts d ON d.token=t.dst_token
                        WHERE t.reference_token=? OR t.amount_paise=?
                        ORDER BY t.posted_at LIMIT 100
                        """,
                (rs, rowNum) -> {
                    String source = rs.getString("src_name");
                    String destination = rs.getString("dst_name");
                    boolean referenceMatch = request.reference() != null
                            && request.reference().equals(rs.getString("reference_token"));
                    boolean victimMatch = request.victimName().equalsIgnoreCase(source)
                            || request.victimName().equalsIgnoreCase(destination);
                    long amount = rs.getLong("amount_paise");
                    Instant postedAt = rs.getObject("posted_at", OffsetDateTime.class).toInstant();
                    MatchingService.Candidate score = matching.score(referenceMatch, request.amountPaise(),
                            amount, victimMatch, request.incidentAt(), postedAt);
                    String basis = "Reference, amount, victim, and time indicators";
                    return new MatchCandidate(rs.getString("txn_id"), score.score(),
                            amount, postedAt.toString(), source, destination, basis,
                            rs.getString("source_label"));
                }, caseComplaint.referenceToken(), request.amountPaise());
        candidates.sort(Comparator.comparingDouble(MatchCandidate::score).reversed());
        return candidates;
    }

    @GetMapping("/api/audit/verify")
    @ResponseBody
    public Map<String, Object> verifyAudit() {
        return Map.of("valid", audit.verifyChain());
    }

    @PostMapping("/api/cases/{caseId}/confirm")
    @ResponseBody
    public Map<String, Object> confirmLink(@PathVariable("caseId") String caseId,
                                           @RequestBody ConfirmRequest req, Authentication auth) {
        if (!auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_INVESTIGATING_OFFICER"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE txn_id=?",
                Integer.class, req.txnId());
        if (exists == null || exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
        }
        jdbc.update("MERGE INTO case_links(case_id,txn_id,score,basis,confirmed_by,confirmed_at) "
                        + "KEY(case_id,txn_id) VALUES(?,?,?,?,?,?)",
                caseId, req.txnId(), req.score(), req.basis(), auth.getName(), Instant.now());
        jdbc.update("UPDATE cases SET confirmed_txn_id=?,status='MATCHED' WHERE case_id=?",
                req.txnId(), caseId);
        audit.append(auth.getName(), "MATCH_CONFIRMED", caseId + "|" + req.txnId() + "|" + req.basis());
        return Map.of("confirmed", true, "caseId", caseId, "txnId", req.txnId());
    }

    @PostMapping(value = "/api/detokenise", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('INVESTIGATING_OFFICER')")
    @ResponseBody
    public Map<String, String> detokenise(@RequestBody DetokeniseRequest req, Authentication auth) {
        if (req.reason() == null || req.reason().trim().length() < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Written reason of at least 12 characters required");
        }
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM trail_edges WHERE case_id=? AND (src_token=? OR dst_token=?)",
                Integer.class, req.caseId(), req.token(), req.token());
        if (active == null || active == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Account is outside the active complaint trail");
        }
        List<Map<String, Object>> vault = jdbc.queryForList(
                "SELECT encrypted_value,nonce FROM token_vault WHERE token=?", req.token());
        audit.append(auth.getName(), "DETOKENISE", req.caseId() + "|" + req.token() + "|" + req.reason());
        if (vault.isEmpty()) {
            return Map.of("masked", "(xxxx••••)", "status", "No vault mapping available");
        }
        return Map.of("value", tokens.decrypt(vault.get(0).get("ENCRYPTED_VALUE").toString(),
                vault.get(0).get("NONCE").toString()));
    }

    @PostMapping("/api/reviews")
    @ResponseBody
    public Map<String, Object> review(@RequestBody ReviewRequest req, Authentication auth) {
        if (!auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_REVIEWER"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (req.reason() == null || req.reason().trim().length() < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Written reason required");
        }
        Long balance = jdbc.query("SELECT balance_paise FROM accounts WHERE token=?",
                rs -> rs.next() ? rs.getLong(1) : null, req.token());
        if (balance == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        long lien = Math.min(Math.max(0, req.lienPaise()), balance);
        jdbc.update("INSERT INTO reviews(case_id,account_token,reviewer,decision,reason,lien_paise,reviewed_at) "
                        + "VALUES(?,?,?,?,?,?,?)",
                req.caseId(), req.token(), auth.getName(), req.decision(), req.reason(), lien, Instant.now());
        audit.append(auth.getName(), "REVIEW_" + req.decision(),
                req.caseId() + "|" + req.token() + "|lienPaise=" + lien);
        return Map.of("saved", true, "lienPaise", lien, "decision", req.decision());
    }

    @GetMapping("/api/cases/{caseId}/transactions")
    @ResponseBody
    public List<Map<String, Object>> transactions(@PathVariable("caseId") String caseId) {
        return jdbc.queryForList("""
                SELECT t.txn_id,t.posted_at,t.amount_paise,t.rail,s.display_name src_name,d.display_name dst_name
                FROM transactions t JOIN cases c ON c.case_id=?
                JOIN case_links l ON l.case_id=c.case_id AND l.txn_id=t.txn_id
                LEFT JOIN accounts s ON s.token=t.src_token
                LEFT JOIN accounts d ON d.token=t.dst_token ORDER BY t.posted_at
                """, caseId);
    }

    @GetMapping(value = "/api/reports/{caseId}.pdf", produces = "application/pdf")
    @ResponseBody
    public byte[] report(@PathVariable("caseId") String caseId, HttpServletResponse response,
                        Authentication auth) {
        byte[] pdf = reports.createPdf(caseId);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + caseId + ".pdf\"");
        audit.append(auth.getName(), "REPORT_DOWNLOADED", caseId);
        return pdf;
    }

    @PostMapping(value = "/api/reports/{caseId}/verify", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> verifyReport(@PathVariable("caseId") String caseId,
                                            @RequestBody byte[] content) {
        return reports.verify(caseId, content);
    }

    private ComplaintData complaint(String caseId) {
        return jdbc.queryForObject("""
                        SELECT c.complaint_ref,c.victim_name,c.victim_token,c.amount_paise,c.incident_at,
                               c.payer_bank,c.reference_token
                        FROM complaints c JOIN cases ca ON ca.complaint_id=c.id WHERE ca.case_id=?
                        """,
                (rs, rowNum) -> new ComplaintData(rs.getString("complaint_ref"),
                        rs.getString("victim_name"), rs.getString("victim_token"),
                        rs.getLong("amount_paise"), rs.getObject("incident_at", OffsetDateTime.class),
                        rs.getString("payer_bank"), rs.getString("reference_token")), caseId);
    }

    private void addLayout(Model model, Authentication auth, String activePage) {
        List<String> roles = auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        model.addAttribute("username", auth.getName());
        model.addAttribute("activePage", activePage);
        model.addAttribute("canIntake", roles.contains("ROLE_INVESTIGATING_OFFICER"));
        model.addAttribute("canTrail", roles.contains("ROLE_INVESTIGATING_OFFICER")
                || roles.contains("ROLE_REVIEWER"));
        model.addAttribute("canTriage", roles.contains("ROLE_INVESTIGATING_OFFICER")
                || roles.contains("ROLE_REVIEWER") || roles.contains("ROLE_BANK_ANALYST"));
        model.addAttribute("canReview", roles.contains("ROLE_REVIEWER"));
        model.addAttribute("canReports", roles.contains("ROLE_INVESTIGATING_OFFICER")
                || roles.contains("ROLE_AUDITOR"));
    }

    private List<Map<String, Object>> graphNodes(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            addNode(nodes, row.get("SRC_TOKEN").toString(), row.get("SRC_NAME"),
                    row.get("SRC_ROLE"), ((Number) row.get("AMOUNT_PAISE")).longValue(),
                    row.get("EVENT_AT"));
            addNode(nodes, row.get("DST_TOKEN").toString(), row.get("DST_NAME"),
                    row.get("DST_ROLE"), ((Number) row.get("AMOUNT_PAISE")).longValue(),
                    row.get("EVENT_AT"));
        }
        return new ArrayList<>(nodes.values());
    }

    private void addNode(Map<String, Map<String, Object>> nodes, String token, Object nameValue,
                         Object roleValue, long amountPaise, Object eventTime) {
        if (nodes.containsKey(token)) {
            return;
        }
        String name = nameValue == null ? "Unknown account" : nameValue.toString();
        String role = roleValue == null ? "Suspected lead" : roleValue.toString();
        String kind = name.equals("Sunita Devi") ? "victim"
                : role.equalsIgnoreCase("Aggregator candidate") ? "aggregator"
                : role.equalsIgnoreCase("Relay") ? "relay" : "other";
        String at = eventTime instanceof OffsetDateTime value
                ? value.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)) : "";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", token);
        data.put("label", name + "\n₹" + String.format(Locale.ROOT, "%,d", amountPaise / 100) + "\n" + at);
        data.put("kind", kind);
        nodes.put(token, Map.of("data", data));
    }

    private List<Map<String, Object>> graphEdges(List<Map<String, Object>> rows) {
        List<Map<String, Object>> edges = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> row : rows) {
            long amountPaise = ((Number) row.get("AMOUNT_PAISE")).longValue();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", "edge" + index++);
            data.put("source", row.get("SRC_TOKEN").toString());
            data.put("target", row.get("DST_TOKEN").toString());
            data.put("label", "₹" + String.format(Locale.ROOT, "%,d", amountPaise / 100));
            data.put("weight", 3);
            data.put("unobserved", false);
            edges.add(Map.of("data", data));
        }
        return edges;
    }

    public record ConfirmRequest(String txnId, double score, String basis) {}
    public record DetokeniseRequest(String caseId, String token, String reason) {}
    public record ReviewRequest(String caseId, String token, String decision, String reason, long lienPaise) {}
    public record MatchRequest(String ncrpId, String victimName, long amountPaise, Instant incidentAt,
                               String payerBank, String reference) {}
    public record MatchCandidate(String txnId, double score, long amountPaise,
                                 String postedAt, String sourceName, String destinationName,
                                 String basis, String sourceLabel) {}
    private record ComplaintData(String complaintRef, String victimName, String victimToken, long amountPaise,
                                 OffsetDateTime incidentAt, String payerBank, String referenceToken) {}
}
