package fi.agentgate.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fi.agentgate.user.AppUser;
import fi.agentgate.user.Role;

@Service
public class JwtService {

    private final String secret;
    private final long ttlMinutes;
    private SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
        this.secret = secret;
        this.ttlMinutes = ttlMinutes;
    }

    @PostConstruct
    void init() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least 32 characters long (see .env.example)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public long ttlSeconds() {
        return Duration.ofMinutes(ttlMinutes).toSeconds();
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(ttlMinutes))))
                .signWith(key)
                .compact();
    }

    /** Throws a JwtException / IllegalArgumentException when the token is invalid or expired. */
    public AuthPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new AuthPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get("username", String.class),
                Role.valueOf(claims.get("role", String.class)));
    }
}
