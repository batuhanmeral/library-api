package com.batuhanmeral.library.auth;

import com.batuhanmeral.library.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Issues and reads the access tokens: JWTs (JSON Web Tokens, RFC 7519) signed with HMAC-SHA256.
 *
 * <p>A token is three Base64URL parts joined by dots: a header ({@code {"alg":"HS256"}}), the
 * claims ({@code {"iss":"library-api","sub":"12","role":"MEMBER","iat":...,"exp":...}}) and a
 * signature over the first two. Anyone can read the claims; only the holder of the secret can
 * produce a signature that matches them, so a changed role or user id is detected.
 *
 * <p>The claims hold the user id and the role, nothing personal: a token may end up in logs or
 * browser storage. Reading a token does not touch the database. That makes every request cheaper,
 * but a token stays valid until it expires even if the user's role changes in the meantime; the
 * short lifetime limits that, and rules that depend on the current state (LoanService: only members
 * borrow) check the database themselves.
 *
 * <p>A {@code @Component}, not a {@code @Service}: it runs on every request with a token, and the
 * timing aspect (which wraps services) would add a log line to each.
 */
@Component
public class JwtService {

    private static final String ROLE = "role";

    private final JwtProperties properties;

    private final Clock clock;

    private final SecretKey key;

    private final JwtParser parser;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = signingKey(properties.secret());
        this.parser =
                Jwts.parser()
                        // Only tokens signed with this key; an unsigned token ("alg": "none") or
                        // one signed with a key pair is refused.
                        .verifyWith(key)
                        .requireIssuer(properties.issuer())
                        // "exp" is checked against the application's clock, as everything else.
                        .clock(() -> Date.from(clock.instant()))
                        .build();
    }

    public AccessToken issue(AuthenticatedUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.expiration());
        String token =
                Jwts.builder()
                        .issuer(properties.issuer())
                        .subject(user.id().toString())
                        .claim(ROLE, user.role().name())
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiresAt))
                        .signWith(key, Jwts.SIG.HS256)
                        .compact();
        return new AccessToken(token, expiresAt);
    }

    /**
     * Checks the signature, the issuer and the expiry, then reads the user.
     *
     * @throws InvalidTokenException whatever is wrong with the token
     */
    public AuthenticatedUser parse(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            String role = claims.get(ROLE, String.class);
            if (claims.getSubject() == null || role == null) {
                throw new InvalidTokenException();
            }
            return new AuthenticatedUser(Long.valueOf(claims.getSubject()), Role.valueOf(role));
        } catch (JwtException | IllegalArgumentException e) {
            // IllegalArgumentException: an empty token, a subject that is not a number, an
            // unknown role. Signed by us, so these would be bugs, but they must not become 500s.
            throw new InvalidTokenException();
        }
    }

    private static SecretKey signingKey(String secret) {
        try {
            // Keys.hmacShaKeyFor refuses keys shorter than 256 bits, the size of the HS256 hash.
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "library.security.jwt.secret must be Base64 of at least 32 random bytes,"
                            + " e.g. the output of: openssl rand -base64 32",
                    e);
        }
    }
}
