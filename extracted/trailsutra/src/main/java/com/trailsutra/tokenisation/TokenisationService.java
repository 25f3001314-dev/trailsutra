package com.trailsutra.tokenisation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TokenisationService {
    private final byte[] hmacKey;
    private final byte[] encryptionKey;
    private final SecureRandom random = new SecureRandom();
    public TokenisationService(@Value("${trailsutra.crypto.hmac-key}") String hmacKey,
                               @Value("${trailsutra.crypto.encryption-key}") String encryptionKey) {
        this.hmacKey = hmacKey.getBytes(StandardCharsets.UTF_8);
        try { this.encryptionKey = MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public String accountToken(String bankId, String accountId) { return hmac("ACCOUNT|" + canon(bankId) + "|" + canon(accountId)); }
    public String mobileToken(String mobile) { return hmac("MOBILE|" + canon(mobile)); }
    public String endpointToken(String kind, String value) { return hmac(canon(kind) + "|" + canon(value)); }
    private String canon(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT); }
    private String hmac(String value) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(hmacKey, "HmacSHA256")); return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Token generation failed", e); }
    }
    public EncryptedValue encrypt(String value) {
        try { byte[] nonce = new byte[12]; random.nextBytes(nonce); Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
            return new EncryptedValue(Base64.getEncoder().encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8))), Base64.getEncoder().encodeToString(nonce)); }
        catch (Exception e) { throw new IllegalStateException("Vault encryption failed", e); }
    }
    public String decrypt(String cipherText, String nonce) {
        try { Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(nonce))); return new String(c.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8); }
        catch (Exception e) { throw new IllegalStateException("Vault decryption failed", e); }
    }
    public record EncryptedValue(String ciphertext, String nonce) { }
}
