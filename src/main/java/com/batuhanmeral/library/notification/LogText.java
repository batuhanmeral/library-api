package com.batuhanmeral.library.notification;

import java.util.regex.Pattern;

/**
 * Değerleri log'a yazılabilir hale getirir. E-posta ve telefon maskelenir; kullanıcıdan gelen metin
 * tek satıra indirilir, sahte log satırı üretemesin.
 */
final class LogText {

    private static final String HIDDEN = "***";

    /**
     * Kontrol karakterleri (Cc), görünmez biçim karakterleri (Cf), satır ve paragraf ayırıcıları
     * (Zl, Zp).
     */
    private static final Pattern UNSAFE = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]");

    private LogText() {}

    /**
     * {@code member@example.com}, {@code m***@example.com} olur; alan adı son "@"tan sonra başlar.
     */
    static String maskEmail(String email) {
        int at = email.lastIndexOf('@');
        if (at < 1) {
            return HIDDEN;
        }
        return email.charAt(0) + HIDDEN + email.substring(at);
    }

    /** {@code +1 555 555 0100}, {@code ***00} olur. */
    static String maskPhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return HIDDEN;
        }
        return HIDDEN + digits.substring(digits.length() - 2);
    }

    /** Satır sonu, tab ve diğer kontrol ve görünmez karakterleri boşluk yapar. */
    static String oneLine(String text) {
        return UNSAFE.matcher(text).replaceAll(" ");
    }
}
