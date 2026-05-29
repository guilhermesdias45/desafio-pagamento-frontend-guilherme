package com.acaboumony.user.validation;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.dto.request.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates conditional rules for {@link com.acaboumony.user.dto.request.RegisterRequest}:
 * <ul>
 *   <li>STAFF role → violation with message {@code INVALID_ROLE} on field {@code role}.</li>
 *   <li>MERCHANT_OWNER + blank companyName or cnpj → violation with message
 *       {@code MISSING_MERCHANT_DATA} on field {@code companyName} / {@code cnpj}.</li>
 *   <li>CUSTOMER → always valid (companyName and cnpj are ignored).</li>
 * </ul>
 */
public class RegisterRequestValidator implements ConstraintValidator<ValidRegisterRequest, RegisterRequest> {

    @Override
    public boolean isValid(RegisterRequest request, ConstraintValidatorContext ctx) {
        if (request == null || request.role() == null) {
            return true; // null handled by @NotNull on individual fields
        }

        ctx.disableDefaultConstraintViolation();

        if (request.role() == UserRole.STAFF) {
            ctx.buildConstraintViolationWithTemplate("INVALID_ROLE")
               .addPropertyNode("role")
               .addConstraintViolation();
            return false;
        }

        if (request.role() == UserRole.MERCHANT_OWNER) {
            boolean companyNameBlank = request.companyName() == null || request.companyName().isBlank();
            boolean cnpjBlank = request.cnpj() == null || request.cnpj().isBlank();

            if (companyNameBlank || cnpjBlank) {
                if (companyNameBlank) {
                    ctx.buildConstraintViolationWithTemplate("MISSING_MERCHANT_DATA")
                       .addPropertyNode("companyName")
                       .addConstraintViolation();
                }
                if (cnpjBlank) {
                    ctx.buildConstraintViolationWithTemplate("MISSING_MERCHANT_DATA")
                       .addPropertyNode("cnpj")
                       .addConstraintViolation();
                }
                return false;
            }
        }

        return true;
    }
}
