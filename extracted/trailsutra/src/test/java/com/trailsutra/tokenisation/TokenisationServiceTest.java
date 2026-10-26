package com.trailsutra.tokenisation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class TokenisationServiceTest {
 @Test void tokensAreDeterministicAndDomainSeparated(){TokenisationService s=new TokenisationService("hmac-secret","encryption-secret");assertThat(s.accountToken("Bank A","00123")).isEqualTo(s.accountToken("bank a","00123"));assertThat(s.accountToken("Bank A","00123")).isNotEqualTo(s.mobileToken("00123"));}
 @Test void vaultEncryptionRoundTrips(){TokenisationService s=new TokenisationService("hmac-secret","encryption-secret");var e=s.encrypt("sensitive-value");assertThat(s.decrypt(e.ciphertext(),e.nonce())).isEqualTo("sensitive-value");assertThat(e.ciphertext()).isNotEqualTo("sensitive-value");}
}
