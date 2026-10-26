package com.trailsutra.matching;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
class MatchingServiceTest {
 @Test void exactReferenceAmountVictimAndTimeProducesHighConfidence(){var s=new MatchingService(.4,.25,.2,.15,.9,.15,.7,30);var c=s.score(true,10000,10000,true,Instant.EPOCH,Instant.EPOCH);assertThat(c.score()).isEqualTo(1.0);assertThat(c.verifiedReferenceCandidate()).isTrue();assertThat(s.hasMargin(.96,.78)).isTrue();}
 @Test void noisyReferenceIsNotVerified(){var s=new MatchingService(.4,.25,.2,.15,.9,.15,.7,30);var c=s.score(false,10000,10000,true,Instant.EPOCH,Instant.EPOCH);assertThat(c.verifiedReferenceCandidate()).isFalse();}
}
