package com.batuhanmeral.library.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Access token ayarları ({@code library.security.jwt.*}).
 *
 * @param secret HMAC anahtarı, en az 32 rastgele baytın Base64'ü; ortamdan gelir (JWT_SECRET),
 *     repoya yazılmaz
 * @param expiration token'ın verildikten sonra geçerli kaldığı süre
 * @param issuer her token'a yazılan ve okurken aranan "iss"
 */
@ConfigurationProperties("library.security.jwt")
@Validated
public record JwtProperties(
        @NotBlank String secret, @NotNull Duration expiration, @NotBlank String issuer) {

    /** Secret, özellikleri yazdıran log ve hata mesajlarına düşmesin. */
    @Override
    public String toString() {
        return "JwtProperties[secret=***, expiration=%s, issuer=%s]".formatted(expiration, issuer);
    }
}
