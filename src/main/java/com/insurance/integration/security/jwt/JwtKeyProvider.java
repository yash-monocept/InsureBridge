package com.insurance.integration.security.jwt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Supplies the RSA key pair used to sign and verify JWTs (RS256).
 *
 * <p>Production deployments should provide PEM-encoded keys through
 * {@code app.security.jwt.private-key} and {@code app.security.jwt.public-key}.
 * When they are absent (local development, tests), a 2048-bit RSA key pair is
 * generated in memory. Ephemeral keys mean tokens are invalidated on restart —
 * acceptable for development, not for production.</p>
 */
@Component
@Slf4j
public class JwtKeyProvider {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public JwtKeyProvider(
            @Value("${app.security.jwt.private-key:}") String privateKeyPem,
            @Value("${app.security.jwt.public-key:}") String publicKeyPem) {

        if (!privateKeyPem.isBlank() && !publicKeyPem.isBlank()) {
            this.privateKey = parsePrivateKey(privateKeyPem);
            this.publicKey = parsePublicKey(publicKeyPem);
            log.info("JWT signing keys loaded from configuration (RS256)");
        } else {
            KeyPair keyPair = generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();
            log.warn("No JWT key pair configured — generated an ephemeral RSA key pair. "
                    + "Tokens will be invalid after restart. Configure app.security.jwt.private-key "
                    + "and app.security.jwt.public-key for persistent deployments.");
        }
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate RSA key pair for JWT signing", e);
        }
    }

    private static PrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = decodePem(pem, "PRIVATE KEY");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid app.security.jwt.private-key (expected PKCS#8 PEM)", e);
        }
    }

    private static PublicKey parsePublicKey(String pem) {
        try {
            byte[] der = decodePem(pem, "PUBLIC KEY");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid app.security.jwt.public-key (expected X.509 PEM)", e);
        }
    }

    private static byte[] decodePem(String pem, String label) {
        String normalised = pem
                .replace("\\n", "\n")
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalised);
    }
}
