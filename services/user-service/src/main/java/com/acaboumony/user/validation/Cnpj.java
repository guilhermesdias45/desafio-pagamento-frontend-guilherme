package com.acaboumony.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a {@code String} is a syntactically valid Brazilian CNPJ.
 *
 * <p>The annotated value must be exactly 14 numeric digits (no punctuation) and must pass the
 * Módulo 11 check-digit algorithm defined by Receita Federal. {@code null} values are accepted —
 * use {@code @NotNull} or {@code @NotBlank} separately to enforce presence.</p>
 *
 * <p>The controller layer is responsible for stripping punctuation
 * ({@code "11.222.333/0001-81"} → {@code "11222333000181"}) before validation.</p>
 */
@Documented
@Constraint(validatedBy = CnpjValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Cnpj {

    String message() default "INVALID_CNPJ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
