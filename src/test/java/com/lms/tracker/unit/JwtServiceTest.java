package com.lms.tracker.unit;

import com.lms.tracker.entity.RoleName;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.security.InvalidTokenException;
import com.lms.tracker.security.IssuedToken;
import com.lms.tracker.security.JwtService;
import com.lms.tracker.security.SecurityProperties;
import com.lms.tracker.security.TokenClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("unit-test-secret-key-with-at-least-32-bytes!".getBytes());
    private static final String ISSUER = "lms-progress-tracker";

    private static JwtService service(String secret) {
        return new JwtService(new SecurityProperties(
                new SecurityProperties.Jwt(secret, Duration.ofHours(1), ISSUER),
                4,
                new SecurityProperties.Login(5, Duration.ofMinutes(15))));
    }

    private static AuthenticatedUser user() {
        return new AuthenticatedUser(42L, "user@test.local", "Test User", RoleName.STUDENT, true, null);
    }

    @Test
    void issuedTokenRoundTrips() {
        JwtService jwt = service(SECRET);
        IssuedToken issued = jwt.issue(user());

        TokenClaims claims = jwt.parse(issued.token());
        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.tokenId()).isEqualTo(issued.tokenId());
        assertThat(claims.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void everyTokenHasAUniqueId() {
        JwtService jwt = service(SECRET);
        assertThat(jwt.issue(user()).tokenId()).isNotEqualTo(jwt.issue(user()).tokenId());
    }

    @Test
    void tamperedSignatureIsRejected() {
        JwtService jwt = service(SECRET);
        String token = jwt.issue(user()).token();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThatThrownBy(() -> jwt.parse(tampered)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void tokenFromAnotherKeyIsRejected() {
        String otherSecret = Base64.getEncoder().encodeToString("another-secret-key-that-is-32-bytes-long!!".getBytes());
        String foreign = service(otherSecret).issue(user()).token();
        assertThatThrownBy(() -> service(SECRET).parse(foreign)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = Jwts.builder()
                .subject("42").id("old").issuer(ISSUER)
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();
        assertThatThrownBy(() -> service(SECRET).parse(expired)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void wrongIssuerIsRejected() {
        String token = Jwts.builder()
                .subject("42").id("x").issuer("someone-else")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();
        assertThatThrownBy(() -> service(SECRET).parse(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void weakSecretFailsFastAtStartup() {
        String weak = Base64.getEncoder().encodeToString("too-short".getBytes());
        assertThatThrownBy(() -> service(weak)).isInstanceOf(IllegalStateException.class);
    }
}
