package com.batuhanmeral.library.auth.dto;

import com.batuhanmeral.library.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /auth/login gövdesi. Format kontrolü yok; şifrede kayıttaki 72 bayt sınırı var, BCrypt'e
 * daha uzunu gitmez.
 */
public record LoginRequest(
        @NotBlank @Size(max = 254) String email, @NotBlank @MaxUtf8Bytes(72) String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=%s]".formatted(email);
    }
}
