package com.batuhanmeral.library.auth.dto;

import com.batuhanmeral.library.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /auth/login. No format checks: a wrong address simply has no account. The password
 * has the 72-byte limit of registration, so no longer one reaches BCrypt.
 */
public record LoginRequest(
        @NotBlank @Size(max = 254) String email, @NotBlank @MaxUtf8Bytes(72) String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=%s]".formatted(email);
    }
}
