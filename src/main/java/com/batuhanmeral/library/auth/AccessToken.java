package com.batuhanmeral.library.auth;

import java.time.Instant;

/** A signed access token and the moment it stops being accepted. */
public record AccessToken(String token, Instant expiresAt) {}
