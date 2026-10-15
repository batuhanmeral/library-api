package com.batuhanmeral.library.auth.dto;

import com.batuhanmeral.library.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /auth/register gövdesi. Rol alanı yok: kayıt hep üye oluşturur.
 *
 * @param phone isteğe bağlı, E.164 biçiminde ("+" ve ülke kodu); gecikme SMS'leri için
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8) @MaxUtf8Bytes(72) String password,
        @NotBlank @Size(max = 200) String fullName,
        @Pattern(regexp = "\\+[1-9][0-9]{6,14}", message = "must be like +905551234567")
                String phone) {

    /** Şifre, isteği yazdıran log ve hata mesajlarına düşmesin. */
    @Override
    public String toString() {
        return "RegisterRequest[email=%s, fullName=%s, phone=%s]".formatted(email, fullName, phone);
    }
}
