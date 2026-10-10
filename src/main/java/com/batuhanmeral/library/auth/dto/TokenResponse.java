package com.batuhanmeral.library.auth.dto;

/**
 * Answer of a successful login, in the shape OAuth 2 token responses use.
 *
 * @param tokenType always "Bearer": send the token as {@code Authorization: Bearer <accessToken>}
 * @param expiresIn seconds until the token is no longer accepted
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
