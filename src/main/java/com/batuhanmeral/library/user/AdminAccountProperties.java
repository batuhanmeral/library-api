package com.batuhanmeral.library.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Başlangıçta oluşturulan admin hesabı ({@code library.security.admin.*}). Kayıt sadece üye
 * oluşturur, ilk admin ortamdan gelir (ADMIN_EMAIL, ADMIN_PASSWORD).
 *
 * @param email hesabın e-postası; boşsa hesap oluşturulmaz
 * @param password en az 8 karakter, en fazla 72 bayt; sadece BCrypt hash'i saklanır
 * @param fullName hesabın görünen adı, verilmezse "Administrator"
 */
@ConfigurationProperties("library.security.admin")
public record AdminAccountProperties(String email, String password, String fullName) {

    @Override
    public String toString() {
        return "AdminAccountProperties[email=%s, password=***, fullName=%s]"
                .formatted(email, fullName);
    }
}
