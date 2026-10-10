package com.batuhanmeral.library.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batuhanmeral.library.MutableClock;
import com.batuhanmeral.library.user.Role;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Plain unit test: JwtService needs only its settings and a clock. */
class JwtServiceTest {

    private static final String SECRET = base64("0123456789abcdef0123456789abcdef");

    private static final String OTHER_SECRET = base64("fedcba9876543210fedcba9876543210");

    private final MutableClock clock =
            new MutableClock(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);

    private final JwtService jwt = service(SECRET, "library-api");

    @Test
    void aTokenItIssuedIsReadBackAsTheSameUser() {
        AccessToken token = jwt.issue(new AuthenticatedUser(42L, Role.MEMBER));

        assertThat(jwt.parse(token.token())).isEqualTo(new AuthenticatedUser(42L, Role.MEMBER));
        assertThat(token.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(1)));
    }

    @Test
    void theTokenHoldsTheIdAndTheRoleButNoPersonalData() {
        String token = jwt.issue(new AuthenticatedUser(42L, Role.ADMIN)).token();

        // A JWT is signed, not encrypted: anyone can read the middle part.
        String payload =
                new String(
                        Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                        StandardCharsets.UTF_8);
        assertThat(payload)
                .contains("\"sub\":\"42\"", "\"role\":\"ADMIN\"", "\"iss\":\"library-api\"")
                .doesNotContain("@");
    }

    @Test
    void anExpiredTokenIsRejected() {
        String token = jwt.issue(new AuthenticatedUser(42L, Role.MEMBER)).token();
        clock.advance(Duration.ofHours(1).plusSeconds(1));

        assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() {
        String forged =
                service(OTHER_SECRET, "library-api")
                        .issue(new AuthenticatedUser(1L, Role.ADMIN))
                        .token();

        assertThatThrownBy(() -> jwt.parse(forged)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void aTokenFromAnotherIssuerIsRejected() {
        String token =
                service(SECRET, "someone-else")
                        .issue(new AuthenticatedUser(1L, Role.ADMIN))
                        .token();

        assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void aChangedPayloadBreaksTheSignature() {
        String[] parts = jwt.issue(new AuthenticatedUser(42L, Role.MEMBER)).token().split("\\.");
        String payload =
                new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                        .replace("MEMBER", "ADMIN");
        String tampered =
                parts[0]
                        + "."
                        + Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                        + "."
                        + parts[2];

        assertThatThrownBy(() -> jwt.parse(tampered)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void anUnsignedTokenIsRejected() {
        // "alg": "none": a token with no signature at all, a classic attack on careless parsers.
        String unsigned =
                Jwts.builder()
                        .issuer("library-api")
                        .subject("1")
                        .claim("role", "ADMIN")
                        .expiration(Date.from(clock.instant().plusSeconds(60)))
                        .compact();

        assertThatThrownBy(() -> jwt.parse(unsigned)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void aSignedTokenWithoutTheExpectedClaimsIsRejected() {
        String noRole =
                Jwts.builder()
                        .issuer("library-api")
                        .subject("1")
                        .expiration(Date.from(clock.instant().plusSeconds(60)))
                        .signWith(
                                new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256"))
                        .compact();

        assertThatThrownBy(() -> jwt.parse(noRole)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> jwt.parse("not-a-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void aSecretShorterThan32BytesIsRefusedAtStartup() {
        assertThatThrownBy(() -> service(base64("too short"), "library-api"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("library.security.jwt.secret");
    }

    @Test
    void aSecretThatIsNotBase64IsRefusedAtStartup() {
        assertThatThrownBy(() -> service("replace-me-with-openssl-rand-base64-32", "library-api"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("library.security.jwt.secret");
    }

    private JwtService service(String secret, String issuer) {
        return new JwtService(new JwtProperties(secret, Duration.ofHours(1), issuer), clock);
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
