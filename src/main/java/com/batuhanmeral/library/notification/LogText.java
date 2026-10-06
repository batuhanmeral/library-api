package com.batuhanmeral.library.notification;

import java.util.regex.Pattern;

/**
 * Makes values safe to write to a log. Email addresses and phone numbers are personal data: a log
 * is read by more people, kept longer and copied to more places than the user table, so it gets
 * only enough to tell recipients apart. Text that came from users (a book title) could hold line
 * breaks and forge log lines; it is written on one line.
 *
 * <p>"Line break" is more than \n and \r: some log viewers also break lines at NEL (U+0085) and the
 * Unicode line and paragraph separators, and a right-to-left override (U+202E) makes a line look
 * different from what it holds.
 */
final class LogText {

    private static final String HIDDEN = "***";

    /**
     * Control characters, ASCII and C1 (Cc); invisible format characters such as direction
     * overrides and the BOM (Cf); line and paragraph separators (Zl, Zp). {@code \\p{Cntrl}} alone
     * covers only the ASCII controls.
     */
    private static final Pattern UNSAFE = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]");

    private LogText() {}

    /**
     * {@code member@example.com} becomes {@code m***@example.com}. The domain starts after the last
     * "@": a quoted local part may hold one too.
     */
    static String maskEmail(String email) {
        int at = email.lastIndexOf('@');
        if (at < 1) {
            return HIDDEN;
        }
        return email.charAt(0) + HIDDEN + email.substring(at);
    }

    /** {@code +1 555 555 0100} becomes {@code ***00}. */
    static String maskPhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return HIDDEN;
        }
        return HIDDEN + digits.substring(digits.length() - 2);
    }

    /** Replaces line breaks, tabs, other control and invisible format characters with spaces. */
    static String oneLine(String text) {
        return UNSAFE.matcher(text).replaceAll(" ");
    }
}
