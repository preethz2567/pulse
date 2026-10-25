package dev.preethi.pulse.service;

import dev.preethi.pulse.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") Duration expiration
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be null or blank");
        }
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            throw new IllegalArgumentException("JWT expiration must be a positive duration");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("JWT secret must be a valid Base64-encoded string", e);
        }

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 256 bits (32 bytes) for HS256");
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = expiration;
    }

    public String generateToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        Instant now = Instant.now();
        Instant expiry = now.plus(expiration);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
