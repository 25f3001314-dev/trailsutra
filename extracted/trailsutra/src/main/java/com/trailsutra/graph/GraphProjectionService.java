package com.trailsutra.graph;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class GraphProjectionService {
    private final JdbcTemplate jdbc;
    public GraphProjectionService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Map<String,Object>> caseEdges(String caseId){return jdbc.queryForList("SELECT e.src_token,e.dst_token,e.amount_paise,e.tainted_paise,e.event_at,e.evidence_type,e.hop FROM trail_edges e WHERE e.case_id=? ORDER BY e.event_at,e.hop",caseId);}
    public List<Map<String,Object>> sharedHubs(String caseId){return jdbc.queryForList("SELECT ae.endpoint_token,e.kind,COUNT(DISTINCT ae.account_token) account_count FROM account_endpoint ae JOIN endpoints e ON e.token=ae.endpoint_token WHERE ae.account_token IN (SELECT src_token FROM trail_edges WHERE case_id=? UNION SELECT dst_token FROM trail_edges WHERE case_id=?) GROUP BY ae.endpoint_token,e.kind HAVING COUNT(DISTINCT ae.account_token)>1",caseId,caseId);}
}
