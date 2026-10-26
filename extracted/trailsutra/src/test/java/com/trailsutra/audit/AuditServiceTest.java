package com.trailsutra.audit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import static org.assertj.core.api.Assertions.assertThat;
class AuditServiceTest {
 @Test void detectsTampering(){var ds=new DriverManagerDataSource("jdbc:h2:mem:audit;DB_CLOSE_DELAY=-1","sa","");ds.setDriverClassName("org.h2.Driver");Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();JdbcTemplate jdbc=new JdbcTemplate(ds);AuditService s=new AuditService(jdbc,new ObjectMapper());s.append("officer","CASE_OPENED","TS-1");s.append("reviewer","REVIEWED","TS-1");assertThat(s.verifyChain()).isTrue();jdbc.update("UPDATE audit_log SET event_json='tampered' WHERE id=1");assertThat(s.verifyChain()).isFalse();}
}
