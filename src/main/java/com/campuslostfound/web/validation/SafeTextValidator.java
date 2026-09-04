package com.campuslostfound.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SafeTextValidator implements ConstraintValidator<SafeText, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null-ness is @NotNull/@NotBlank's job
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\t' || c == '\r') {
                continue;
            }
            if (Character.isISOControl(c)) {
                return false;
            }
            // bidi overrides / embedding / isolates (U+202A..U+202E, U+2066..U+2069):
            // used to visually disguise the true order of text.
            if ((c >= '‪' && c <= '‮') || (c >= '⁦' && c <= '⁩')) {
                return false;
            }
        }
        return true;
    }
}
