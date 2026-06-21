package com.mykart.auth.service;

import com.mykart.auth.dto.request.LoginRequest;
import com.mykart.auth.dto.request.RefreshTokenRequest;
import com.mykart.auth.dto.request.RegisterRequest;
import com.mykart.auth.dto.response.AuthResponse;
import com.mykart.auth.dto.response.UserResponse;
import com.mykart.auth.entity.RefreshToken;
import com.mykart.auth.entity.User;
import com.mykart.auth.repository.RefreshTokenRepository;
import com.mykart.auth.repository.UserRepository;
import com.mykart.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenExpirationMs;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${auth.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered: " + request.email());
        }
        var user = new User(
                UUID.randomUUID(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role(),
                Instant.now()
        );
        var saved = userRepository.save(user);
        log.info("Registered new user id={} role={}", saved.getId(), saved.getRole());
        return UserResponse.from(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        var user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        var accessToken = jwtService.generateAccessToken(user);
        var rawRefreshToken = generateSecureToken();
        persistRefreshToken(user, rawRefreshToken);

        log.info("User logged in userId={}", user.getId());
        return AuthResponse.of(accessToken, rawRefreshToken, jwtService.getAccessTokenExpirationMs() / 1000);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        var tokenHash = hashToken(request.refreshToken());
        var refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token is expired or revoked");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        var user = refreshToken.getUser();
        var newAccessToken = jwtService.generateAccessToken(user);
        var newRawRefreshToken = generateSecureToken();
        persistRefreshToken(user, newRawRefreshToken);

        return AuthResponse.of(newAccessToken, newRawRefreshToken, jwtService.getAccessTokenExpirationMs() / 1000);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        var tokenHash = hashToken(request.refreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Revoked refresh token for userId={}", token.getUser().getId());
        });
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private void persistRefreshToken(User user, String rawToken) {
        var token = new RefreshToken(
                UUID.randomUUID(),
                user,
                hashToken(rawToken),
                Instant.now().plusMillis(refreshTokenExpirationMs),
                false,
                Instant.now()
        );
        refreshTokenRepository.save(token);
    }

    private String generateSecureToken() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash token", e);
        }
    }
}
