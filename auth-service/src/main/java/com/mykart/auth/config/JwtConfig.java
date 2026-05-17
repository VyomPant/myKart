package com.mykart.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Value("${auth.jwt.private-key}")
    private String privateKeyPem;

    @Bean
    public RSAPrivateKey jwtPrivateKey() throws Exception {
        String stripped = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(stripped);
        var keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    @Bean
    public RSAPublicKey jwtPublicKey(RSAPrivateKey jwtPrivateKey) throws Exception {
        // Derive public key from private key — no need to store the public key separately in auth-service
        var rsaPriv = (RSAPrivateCrtKey) jwtPrivateKey;
        var keyFactory = KeyFactory.getInstance("RSA");
        var publicKeySpec = new RSAPublicKeySpec(rsaPriv.getModulus(), rsaPriv.getPublicExponent());
        return (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
    }
}
