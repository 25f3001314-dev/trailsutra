package com.trailsutra.triage;
import com.trailsutra.trail.AllocationService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class TriageServiceTest {
 @Test void merchantReceivesLimitedLien(){var t=new TriageService(.8,.6,.35,new AllocationService());var d=t.decide(.28,false,true,true,false,4500000,55000000);assertThat(d.role()).isEqualTo("Other account");assertThat(d.lienPaise()).isEqualTo(4500000);assertThat(d.recommendation()).contains("Review tainted amount");}
 @Test void relayNeedsStructuralEvidence(){var t=new TriageService(.8,.6,.35,new AllocationService());assertThat(t.decide(.92,false,false,false,true,0,1000).role()).isNotEqualTo("Relay");assertThat(t.decide(.92,true,false,false,true,0,1000).role()).isEqualTo("Relay");}
}
