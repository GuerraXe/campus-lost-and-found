package com.campuslostfound.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SafeTextValidatorTest {

    private final SafeTextValidator validator = new SafeTextValidator();

    private boolean ok(String s) {
        return validator.isValid(s, null);
    }

    @Test
    void acceptsOrdinaryText() {
        assertThat(ok("Black Dell laptop, left in room 214. Size < 10cm.")).isTrue();
        assertThat(ok("line one\nline two\ttabbed\r")).isTrue();
        assertThat(ok(null)).isTrue();
    }

    @Test
    void rejectsControlCharacters() {
        assertThat(ok("bad\u0000null")).isFalse();   // NUL
        assertThat(ok("bell\u0007here")).isFalse();  // BEL
        assertThat(ok("vtab\u000Bhere")).isFalse();  // vertical tab
    }

    @Test
    void rejectsBidiOverrideCharacters() {
        assertThat(ok("safe\u202Eevil")).isFalse();  // RIGHT-TO-LEFT OVERRIDE
        assertThat(ok("iso\u2066late")).isFalse();   // LEFT-TO-RIGHT ISOLATE
    }

    @Test
    void doesNotStripOrAlterAngleBrackets() {
        // The validator only accepts/rejects; it must not transform. HTML-looking input is
        // valid text and is stored verbatim (output encoding is the client's job).
        assertThat(ok("<script>alert(1)</script>")).isTrue();
    }
}
