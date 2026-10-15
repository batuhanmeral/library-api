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
 * HMAC-SHA256 ile imzalı JWT'leri üretir ve okur.
 *
 * <p>Claim'lerde sadece kullanıcı id'si ve rol var. Okurken veritabanına bakılmaz: rol değişse de
 * token süresi dolana kadar geçerlidir, güncel durum gereken kurallar veritabanına kendisi bakar.
 * {@code @Service} değil {@code @Component}: süre aspect'i her isteğe log satırı eklemesin.
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
                        // Sadece bu anahtarla imzalı token'lar; "alg": "none" ve anahtar çiftiyle
                        // imzalı olanlar reddedilir.
                        .verifyWith(key)
                        .requireIssuer(properties.issuer())
                        // "exp" de uygulamanın saatine göre kontrol edilir.
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
     * İmzayı, issuer'ı ve süreyi kontrol edip kullanıcıyı okur.
     *
     * @throws InvalidTokenException token'da ne sorun olursa
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
            // Boş token, sayı olmayan subject, bilinmeyen rol: bizim imzamızla olmamalı ama 500'e
            // de dönmemeli.
            throw new InvalidTokenException();
        }
    }

    private static SecretKey signingKey(String secret) {
        try {
            // hmacShaKeyFor 256 bitten kısa anahtarı reddeder.
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "library.security.jwt.secret must be Base64 of at least 32 random bytes,"
                            + " e.g. the output of: openssl rand -base64 32",
                    e);
        }
    }
}
