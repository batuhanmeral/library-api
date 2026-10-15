package com.batuhanmeral.library.auth.dto;

/**
 * Başarılı girişin cevabı, OAuth 2 token cevabı biçiminde.
 *
 * @param tokenType hep "Bearer": {@code Authorization: Bearer <accessToken>} olarak gönderilir
 * @param expiresIn token'ın geçerliliğinin bitmesine kalan saniye
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
