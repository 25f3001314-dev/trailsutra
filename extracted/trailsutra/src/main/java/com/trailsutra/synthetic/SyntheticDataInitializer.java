package com.trailsutra.synthetic;

import com.trailsutra.audit.AuditService;
import com.trailsutra.tokenisation.TokenisationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class SyntheticDataInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc; private final TokenisationService tokens; private final AuditService audit;
    public SyntheticDataInitializer(JdbcTemplate jdbc, TokenisationService tokens, AuditService audit) { this.jdbc=jdbc; this.tokens=tokens; this.audit=audit; }
    @Override public void run(String... args) { if (jdbc.queryForObject("SELECT COUNT(*) FROM banks", Integer.class) > 0) return; initialize(); }
    @Transactional public void initialize() {
        jdbc.update("INSERT INTO banks(code,name) VALUES('A','Bank A'),('B','Bank B'),('C','Bank C')");
        long now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toEpochMilli(); Random random = new Random(42); List<Object[]> accounts = new ArrayList<>();
        String[] segments={"salaried","retail merchant","small business","student","household"};
        for(int i=0;i<9993;i++){ String token=tokens.accountToken("BANK"+(i%3+1),"ACCT"+i); accounts.add(new Object[]{token,(long)(i%3+1),Instant.ofEpochMilli(now-random.nextInt(3650)*86400000L),segments[i%5],"STANDARD",false,"Account "+i,"(xxxx"+String.format("%04d",i%10000)+")",random.nextInt(80000000),random.nextInt(200000000)}); }
        String sunita=tokens.accountToken("A","SUN-1001"), a=tokens.accountToken("A","A-1002"), b=tokens.accountToken("B","B-1003"), c=tokens.accountToken("A","C-1004"), d=tokens.accountToken("B","D-1005"), e=tokens.accountToken("C","E-1006"), r=tokens.accountToken("C","R-1007");
        upsertAccount(accounts,sunita,1,"salaried","Sunita Devi","(xxxx1001)",0,0,Instant.parse("2022-01-01T00:00:00Z")); upsertAccount(accounts,a,1,"small business","Gig worker A","(xxxx1002)",0,0,Instant.parse("2024-01-01T00:00:00Z")); upsertAccount(accounts,b,2,"small business","Relay B","(xxxx1003)",0,0,Instant.parse("2023-01-01T00:00:00Z")); upsertAccount(accounts,c,1,"small business","Relay C","(xxxx1004)",0,0,Instant.parse("2023-01-01T00:00:00Z")); upsertAccount(accounts,d,2,"small business","Relay D","(xxxx1005)",0,0,Instant.parse("2023-01-01T00:00:00Z")); upsertAccount(accounts,e,3,"small business","Aggregator E","(xxxx1006)",0,0,Instant.parse("2023-01-01T00:00:00Z")); upsertAccount(accounts,r,3,"retail merchant","Ramesh Verma","(xxxx2468)",55000000,60000000,Instant.parse("2022-10-26T00:00:00Z"));
        jdbc.batchUpdate("INSERT INTO accounts(token,bank_id,opened_at,segment,kyc_tier,is_external,display_name,masked_account,balance_paise,monthly_turnover_paise) VALUES(?,?,?,?,?,?,?,?,?,?)",accounts);
        String[] refs={"TX-SUNITA-01","TX-A-B","TX-A-C","TX-A-D","TX-B-E","TX-C-E","TX-D-R"}; Instant t=Instant.parse("2026-07-15T05:32:00Z");
        Object[][] planted={{refs[0],sunita,a,t,18000000L},{refs[1],a,b,t.plusSeconds(180),6000000L},{refs[2],a,c,t.plusSeconds(240),6000000L},{refs[3],a,d,t.plusSeconds(300),6000000L},{refs[4],b,e,t.plusSeconds(480),6000000L},{refs[5],c,e,t.plusSeconds(480),6000000L},{refs[6],d,r,t.plusSeconds(540),4500000L}};
        List<Object[]> txns=new ArrayList<>(); for(Object[] p:planted) txns.add(new Object[]{p[0],"REF-458",p[1],p[2],p[3],p[3],p[4],"UPI","POSTED",null});
        for(int i=0;i<99993;i++){int from=random.nextInt(10000),to=random.nextInt(10000); if(from==to) to=(to+1)%10000; String src=(String)accounts.get(from)[0], dst=(String)accounts.get(to)[0]; Instant at=Instant.ofEpochMilli(now-random.nextInt(30*24*60*60)*1000L); txns.add(new Object[]{"TX-GEN-"+i,null,src,dst,at,at,10000L+random.nextInt(2000000),new String[]{"UPI","IMPS","NEFT"}[random.nextInt(3)],"POSTED",null}); }
        jdbc.batchUpdate("INSERT INTO transactions(txn_id,reference_token,src_token,dst_token,initiated_at,posted_at,amount_paise,rail,status,source_hash) VALUES(?,?,?,?,?,?,?,?,?,?)",txns,1000,(ps,row)->{for(int i=0;i<10;i++)ps.setObject(i+1,row[i]);});
        jdbc.update("INSERT INTO complaints(complaint_ref,victim_name,victim_token,amount_paise,incident_at,payer_bank,reference_token,status,created_at) VALUES(?,?,?,?,?,?,?,?,?)","NCRP-2026-0715-8932","Sunita Devi",sunita,18000000L,t,"Bank A","REF-458","MATCHED",Instant.now());
        long complaintId=jdbc.queryForObject("SELECT id FROM complaints WHERE complaint_ref=?",Long.class,"NCRP-2026-0715-8932"); jdbc.update("INSERT INTO cases(case_id,complaint_id,status,officer,created_at,confirmed_txn_id) VALUES(?,?,?,?,?,?)","TS-2026-000458",complaintId,"UNDER_REVIEW","SI Rajesh Kumar",Instant.now(),refs[0]);
        for(int i=1;i<200;i++) jdbc.update("INSERT INTO complaints(complaint_ref,victim_name,victim_token,amount_paise,incident_at,payer_bank,reference_token,status,created_at) VALUES(?,?,?,?,?,?,?,?,?)","NCRP-2026-GEN-"+i,"Complainant "+i,tokens.mobileToken("victim-"+i),100000L+random.nextInt(5000000),Instant.ofEpochMilli(now-random.nextInt(30*24*60*60)*1000L),"Bank "+(i%3+1),i%9==0?null:"REF-GEN-"+i,i%7==0?"UNRESOLVED":"RECEIVED",Instant.now());
        String[] accountTokens={sunita,a,b,c,d,e,r}; String[] accountIds={"SUN-1001","A-1002","B-1003","C-1004","D-1005","E-1006","R-1007"};
        for(int i=0;i<accountTokens.length;i++){var encrypted=tokens.encrypt(accountIds[i]);jdbc.update("INSERT INTO token_vault(token,encrypted_value,nonce,created_at) VALUES(?,?,?,?)",accountTokens[i],encrypted.ciphertext(),encrypted.nonce(),Instant.now());}
        for(int i=0;i<planted.length;i++){Object[] p=planted[i];jdbc.update("INSERT INTO case_links(case_id,txn_id,score,basis,confirmed_by,confirmed_at) VALUES(?,?,?,?,?,?)","TS-2026-000458",p[0],i==0?0.96:0.91,"reference + amount + victim + time","SI Rajesh Kumar",Instant.now());jdbc.update("INSERT INTO trail_edges(case_id,txn_id,src_token,dst_token,amount_paise,tainted_paise,event_at,evidence_type,hop) VALUES(?,?,?,?,?,?,?,?,?)","TS-2026-000458",p[0],p[1],p[2],p[4],p[4],p[3],"OBSERVED",i==0?0:i);}
        jdbc.update("INSERT INTO account_scores(case_id,account_token,pre_complaint_score,priority_score,role,recommendation,reasons,tainted_held_paise,created_at) VALUES(?,?,?,?,?,?,?,?,?),(?,?,?,?,?,?,?,?,?),(?,?,?,?,?,?,?,?,?),(?,?,?,?,?,?,?,?,?),(?,?,?,?,?,?,?,?,?),(?,?,?,?,?,?,?,?,?)",
            "TS-2026-000458",a,0.91,0.92,"Relay","Priority review","Rapid movement across linked accounts",0,Instant.now(),
            "TS-2026-000458",b,0.87,0.87,"Relay","Priority review","High pass-through; short dwell",0,Instant.now(),
            "TS-2026-000458",c,0.85,0.85,"Relay","Priority review","High pass-through; fan-in",0,Instant.now(),
            "TS-2026-000458",d,0.82,0.82,"Relay","Priority review","Fast movement; linked trail",0,Instant.now(),
            "TS-2026-000458",e,0.68,0.68,"Aggregator candidate","Watchlist (Review)","Funds from multiple victim-linked accounts",0,Instant.now(),
            "TS-2026-000458",r,0.28,0.28,"Other account","Review tainted amount","Long account history; merchant turnover",4500000,Instant.now());
        jdbc.update("INSERT INTO endpoints(token,kind) VALUES(?,?),(?,?),(?,?)",tokens.endpointToken("device","relay-shared-device"),"device",tokens.endpointToken("pin","relay-shared-pin"),"pin",tokens.endpointToken("ip","hostel-network"),"ip");
        jdbc.update("INSERT INTO account_endpoint(account_token,endpoint_token) VALUES(?,?),(?,?),(?,?)",a,tokens.endpointToken("device","relay-shared-device"),b,tokens.endpointToken("pin","relay-shared-pin"),d,tokens.endpointToken("device","relay-shared-device"));
        jdbc.update("INSERT INTO ground_truth(case_id,account_token,expected_role,expected_positive) VALUES(?,?,?,?),(?,?,?,?),(?,?,?,?),(?,?,?,?),(?,?,?,?),(?,?,?,?)","TS-2026-000458",a,"Relay",true,"TS-2026-000458",b,"Relay",true,"TS-2026-000458",c,"Relay",true,"TS-2026-000458",d,"Relay",true,"TS-2026-000458",e,"Aggregator candidate",true,"TS-2026-000458",r,"Other account",false);
        audit.append("system","INITIAL_DATA_READY","Seeded records with deterministic seed 42");
    }
    private void upsertAccount(List<Object[]> rows,String token,int bank,String segment,String name,String masked,long balance,long turnover,Instant opened){rows.add(new Object[]{token,(long)bank,opened,segment,"VERIFIED",false,name,masked,balance,turnover});}
}
