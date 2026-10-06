package com.batuhanmeral.library.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogTextTest {

    @Test
    void anEmailKeepsTheFirstLetterAndTheDomain() {
        assertThat(LogText.maskEmail("member@example.com")).isEqualTo("m***@example.com");
        assertThat(LogText.maskEmail("a@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void aPhoneKeepsTheLastTwoDigits() {
        assertThat(LogText.maskPhone("+1 555 555 0100")).isEqualTo("***00");
        assertThat(LogText.maskPhone("+15555550142")).isEqualTo("***42");
    }

    @Test
    void valuesThatDoNotLookRightAreHiddenCompletely() {
        assertThat(LogText.maskEmail("no-at-sign")).isEqualTo("***");
        assertThat(LogText.maskEmail("@example.com")).isEqualTo("***");
        assertThat(LogText.maskPhone("12")).isEqualTo("***");
    }

    @Test
    void onlyTheDomainAfterTheLastAtSignIsShown() {
        assertThat(LogText.maskEmail("\"a@b\"@example.com")).isEqualTo("\"***@example.com");
    }

    @Test
    void lineBreaksAndOtherControlCharactersBecomeSpaces() {
        assertThat(LogText.oneLine("Dune\nINFO fake line\r\tend"))
                .isEqualTo("Dune INFO fake line  end");
    }

    @Test
    void unicodeLineSeparatorsAndDirectionOverridesBecomeSpacesToo() {
        // NEL (a C1 control), line and paragraph separators, right-to-left override, BOM.
        assertThat(LogText.oneLine("a\u0085b\u2028c\u2029d\u202Ee\uFEFFf"))
                .isEqualTo("a b c d e f");
        assertThat(LogText.oneLine("Çağlar Kitabı")).isEqualTo("Çağlar Kitabı");
    }
}
