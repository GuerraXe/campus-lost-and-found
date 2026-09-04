package com.campuslostfound.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rejects user-supplied text containing control characters (tab and newline excepted) or
 * Unicode bidirectional-override characters.
 *
 * <p>The application deliberately does <em>not</em> try to sanitize or strip HTML: it
 * stores text verbatim and returns it only as JSON, and hand-rolled HTML stripping is a
 * classic source of both broken content and bypasses. Output encoding is the client's
 * responsibility; the server's job is to reject obviously hostile control input and cap
 * length (see docs/security.md).
 */
@Documented
@Constraint(validatedBy = SafeTextValidator.class)
@Target({java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeText {

    String message() default "must not contain control or direction-override characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
