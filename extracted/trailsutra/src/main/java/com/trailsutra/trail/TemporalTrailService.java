package com.trailsutra.trail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TemporalTrailService {
    private final JdbcTemplate jdbc; private final int maxHops; private final long windowHours;
    public TemporalTrailService(JdbcTemplate jdbc,@Value("${trailsutra.trail.max-hops}") int maxHops,@Value("${trailsutra.trail.window-hours}") long windowHours){this.jdbc=jdbc;this.maxHops=maxHops;this.windowHours=windowHours;}
    @Transactional public int trace(String caseId,String startingTxnId){var root=jdbc.queryForMap("SELECT src_token,dst_token,posted_at,amount_paise FROM transactions WHERE txn_id=?",startingTxnId);String origin=root.get("DST_TOKEN").toString();Instant start=((java.time.OffsetDateTime)root.get("POSTED_AT")).toInstant();long amount=((Number)root.get("AMOUNT_PAISE")).longValue();ArrayDeque<Node> queue=new ArrayDeque<>();queue.add(new Node(origin,start,amount,0));Set<String> seen=new HashSet<>();int edges=0;
        while(!queue.isEmpty()){Node node=queue.removeFirst();if(node.hop()>=maxHops)continue;String visit=node.token()+"|"+node.hop();if(!seen.add(visit))continue;List<Txn> next=jdbc.query("SELECT txn_id,src_token,dst_token,posted_at,amount_paise FROM transactions WHERE src_token=? AND posted_at>? AND posted_at<=? ORDER BY posted_at LIMIT 100",(rs,i)->new Txn(rs.getString(1),rs.getString(2),rs.getString(3),rs.getObject(4,java.time.OffsetDateTime.class).toInstant(),rs.getLong(5)),node.token(),node.at(),start.plus(windowHours,ChronoUnit.HOURS));for(Txn t:next){long tainted=Math.min(node.tainted(),t.amount());jdbc.update("MERGE INTO trail_edges(case_id,txn_id,src_token,dst_token,amount_paise,tainted_paise,event_at,evidence_type,hop) KEY(case_id,txn_id) VALUES(?,?,?,?,?,?,?,?,?)",caseId,t.id(),t.src(),t.dst(),t.amount(),tainted,t.at(),"OBSERVED",node.hop()+1);queue.addLast(new Node(t.dst(),t.at(),tainted,node.hop()+1));edges++;}}
        return edges;
    }
    public record Node(String token,Instant at,long tainted,int hop){} public record Txn(String id,String src,String dst,Instant at,long amount){}
}
