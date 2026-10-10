package com.batuhanmeral.library.user.dto;

import com.batuhanmeral.library.user.Role;
import java.time.Instant;

/** A user as the API returns it. The password hash is never part of a response. */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        Role role,
        Instant createdAt,
        Instant updatedAt) {}
