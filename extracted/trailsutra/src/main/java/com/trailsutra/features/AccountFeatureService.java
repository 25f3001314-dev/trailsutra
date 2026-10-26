package com.trailsutra.features;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AccountFeatureService {
    private final JdbcTemplate jdbc;
    public AccountFeatureService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Map<String,Double> features(String token,Instant at){Map<String,Double> f=new LinkedHashMap<>();Long outgoing=jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE src_token=? AND posted_at BETWEEN ? AND ?",Long.class,token,at.minusSeconds(3600),at);Long incoming=jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE dst_token=? AND posted_at BETWEEN ? AND ?",Long.class,token,at.minusSeconds(3600),at);Long fastOut=jdbc.queryForObject("SELECT COUNT(*) FROM transactions t WHERE t.src_token=? AND t.posted_at BETWEEN ? AND ? AND t.amount_paise >= 0.8 * COALESCE((SELECT SUM(i.amount_paise) FROM transactions i WHERE i.dst_token=t.src_token AND i.posted_at<=t.posted_at AND i.posted_at>=DATEADD('MINUTE',-15,t.posted_at)),0)",Long.class,token,at.minusSeconds(3600),at);Long endpoints=jdbc.queryForObject("SELECT COUNT(DISTINCT ae.endpoint_token) FROM account_endpoint ae WHERE ae.account_token=?",Long.class,token);f.put("fan-out",outgoing==null?0.0:outgoing.doubleValue());f.put("fan-in",incoming==null?0.0:incoming.doubleValue());f.put("pass-through",outgoing==null||outgoing==0?0.0:Math.min(1.0,fastOut.doubleValue()/outgoing));f.put("shared-endpoints",endpoints==null?0.0:endpoints.doubleValue());f.put("burst",Math.min(1.0,(outgoing==null?0:outgoing)/10.0));return Map.copyOf(f);}
}
