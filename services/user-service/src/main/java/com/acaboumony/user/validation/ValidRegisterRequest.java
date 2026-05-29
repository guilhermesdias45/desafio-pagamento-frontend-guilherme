package com.acaboumony.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint that validates the conditional logic in
 * {@link com.acaboumony.user.dto.request.RegisterRequest}:
 * <ul>
 *   <li>{@code MERCHANT_OWNER} → {@code companyName} and {@code cnpj} are mandatory.</li>
 *   <li>{@code CUSTOMER} → {@code companyName} and {@code cnpj} are ignored.</li>
 *   <li>{@code STAFF} → always rejected ({@code INVALID_ROLE}).</li>
 * </ul>
 */
@Documented
@Constraint(validatedBy = RegisterRequestValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRegisterRequest {

    String message() default "Invalid registration request";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
