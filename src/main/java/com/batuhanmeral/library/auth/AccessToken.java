package com.batuhanmeral.library.auth;

import java.time.Instant;

/** İmzalı access token ve geçerliliğinin bittiği an. */
public record AccessToken(String token, Instant expiresAt) {}
