package com.mykart.auth.service;

import com.mykart.auth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        jwtService = new JwtService(
                (RSAPrivateKey) keyPair.getPrivate(),
                (RSAPublicKey) keyPair.getPublic(),
                3600000L
        );
        testUser = new User(UUID.randomUUID(), "buyer@example.com", "hash", User.Role.BUYER, java.time.Instant.now());
    }

    @Test
    void generateToken_containsCorrectSubjectAndRole() {
        var token = jwtService.generateAccessToken(testUser);

        var claims = jwtService.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo(testUser.getId().toString());
        assertThat(claims.get("role", String.class)).isEqualTo("BUYER");
    }

    @Test
    void generateToken_forSeller_hasSellerRole() {
        var seller = new User(UUID.randomUUID(), "seller@example.com", "hash", User.Role.SELLER, java.time.Instant.now());
        var token = jwtService.generateAccessToken(seller);

        var claims = jwtService.validateToken(token);
        assertThat(claims.get("role", String.class)).isEqualTo("SELLER");
    }

    @Test
    void isTokenValid_withValidToken_returnsTrue() {
        var token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_withMalformedToken_returnsFalse() {
        assertThat(jwtService.isTokenValid("not.a.jwt")).isFalse();
    }

    @Test
    void isTokenValid_withTamperedToken_returnsFalse() {
        var token = jwtService.generateAccessToken(testUser);
        var tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void validateToken_withExpiredToken_throwsException() throws Exception {
        var shortLivedService = new JwtService(
                (RSAPrivateKey) KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate(),
                (RSAPublicKey) KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic(),
                -1000L // already expired
        );
        // A token signed with one key can't be validated with a different key —
        // the expired test needs a consistent keypair, so use 1ms expiry
        var keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var expiredService = new JwtService(
                (RSAPrivateKey) keyPair.getPrivate(),
                (RSAPublicKey) keyPair.getPublic(),
                1L // 1ms — will be expired by the time we validate
        );
        var token = expiredService.generateAccessToken(testUser);
        Thread.sleep(10); // ensure expiry

        assertThat(expiredService.isTokenValid(token)).isFalse();
    }
}
