package com.lms.tracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies HS256-signed JWT access tokens.
 * Each token carries a random id (jti) so it can be revoked on logout.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_KEY_BYTES = 32; // 256 bits for HS256

    private final SecretKey signingKey;
    private final JwtParser parser;
    private final Duration expiration;
    private final String issuer;

    public JwtService(SecurityProperties properties) {
        SecurityProperties.Jwt jwt = properties.jwt();
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwt.secret());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("app.security.jwt.secret must be a Base64-encoded key", ex);
        }
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("app.security.jwt.secret must decode to at least 256 bits (32 bytes)");
        }
        if (new String(keyBytes, StandardCharsets.UTF_8).startsWith("dev-only")) {
            log.warn("Using the built-in DEVELOPMENT JWT secret. Set the JWT_SECRET environment variable before deploying.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = jwt.expiration();
        this.issuer = jwt.issuer();
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .clockSkewSeconds(30)
                .build();
    }

    public IssuedToken issue(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiration);
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(tokenId)
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name()) // informational only, authorization re-reads the DB
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, tokenId, Date.from(expiresAt).toInstant());
    }

    /**
     * Verifies signature, issuer and expiry.
     *
     * @throws InvalidTokenException if the token cannot be trusted for any reason
     */
    public TokenClaims parse(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            if (claims.getId() == null || claims.getSubject() == null || claims.getExpiration() == null) {
                throw new InvalidTokenException("Invalid or expired token");
            }
            return new TokenClaims(Long.valueOf(claims.getSubject()), claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid or expired token", ex);
        }
    }

    public Duration getExpiration() {
        return expiration;
    }
}
