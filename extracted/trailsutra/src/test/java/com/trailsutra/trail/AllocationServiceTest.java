package com.trailsutra.trail;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class AllocationServiceTest {
 private final AllocationService service=new AllocationService();
 @Test void lienNeverExceedsHeldTaintedBalance(){assertThat(service.lien(4500000,55000000)).isEqualTo(4500000);assertThat(service.lien(900,500)).isEqualTo(500);}
 @Test void fifoDoesNotAllocateMoreThanAvailable(){var out=service.fifo(100,100,List.of(60L,60L));assertThat(out.get(0).taintedPaise()).isEqualTo(60);assertThat(out.get(1).taintedPaise()).isEqualTo(40);}
 @Test void proportionalAllocationUsesTaintedShare(){var out=service.proportional(500,1000,List.of(200L,200L));assertThat(out).allSatisfy(a->assertThat(a.taintedPaise()).isEqualTo(100));}
}
