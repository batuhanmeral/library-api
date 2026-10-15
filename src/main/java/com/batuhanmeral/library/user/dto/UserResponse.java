package com.batuhanmeral.library.user.dto;

import com.batuhanmeral.library.user.Role;
import java.time.Instant;

/** API'nin döndüğü kullanıcı; şifre hash'i asla cevapta yok. */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        Role role,
        Instant createdAt,
        Instant updatedAt) {}
