package dev.preethi.pulse.service;

import dev.preethi.pulse.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;
    private String validBase64Secret;
    private Duration expiration;

    @BeforeEach
    void setUp() {
        validBase64Secret = generateBase64Secret(32);
        expiration = Duration.ofMinutes(15);
        jwtService = new JwtService(validBase64Secret, expiration);
    }

    private String generateBase64Secret(int byteLength) {
        byte[] bytes = new byte[byteLength];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void generateToken_withValidUser_createsSignedJwtWithCorrectClaims() {
        User user = new User("alice@example.com", "hashedPassword", "ROLE_USER");
        user.setId(42L);

        long beforeGeneration = System.currentTimeMillis();
        String token = jwtService.generateToken(user);
        long afterGeneration = System.currentTimeMillis();

        assertNotNull(token);
        assertFalse(token.isBlank());

        Claims claims = jwtService.extractAllClaims(token);

        assertEquals("alice@example.com", claims.getSubject());
        assertEquals(42L, claims.get("userId", Number.class).longValue());
        assertEquals("ROLE_USER", claims.get("role", String.class));

        Date issuedAt = claims.getIssuedAt();
        Date expirationDate = claims.getExpiration();

        assertNotNull(issuedAt);
        assertNotNull(expirationDate);

        // Issued at should be around the time of token generation (within 2 seconds tolerance)
        assertTrue(issuedAt.getTime() >= beforeGeneration - 2000);
        assertTrue(issuedAt.getTime() <= afterGeneration + 2000);

        // Expiration should be ~15 minutes after issuedAt
        long diffMs = expirationDate.getTime() - issuedAt.getTime();
        assertEquals(15 * 60 * 1000, diffMs);
    }

    @Test
    void constructor_withNullOrBlankSecret_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService(null, expiration));
        assertThrows(IllegalArgumentException.class, () -> new JwtService("", expiration));
        assertThrows(IllegalArgumentException.class, () -> new JwtService("   ", expiration));
    }

    @Test
    void constructor_withInvalidBase64Secret_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("invalid_base64_!@#$%", expiration));
    }

    @Test
    void constructor_withSecretShorterThan32Bytes_throwsIllegalArgumentException() {
        String shortSecret = generateBase64Secret(16); // 128 bits
        assertThrows(IllegalArgumentException.class, () -> new JwtService(shortSecret, expiration));
    }

    @Test
    void constructor_withNullOrNonPositiveExpiration_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService(validBase64Secret, null));
        assertThrows(IllegalArgumentException.class, () -> new JwtService(validBase64Secret, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new JwtService(validBase64Secret, Duration.ofSeconds(-10)));
    }

    @Test
    void generateToken_withNullUser_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> jwtService.generateToken(null));
    }
}
