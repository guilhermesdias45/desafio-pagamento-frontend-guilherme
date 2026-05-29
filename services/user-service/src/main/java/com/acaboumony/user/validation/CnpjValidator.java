package com.acaboumony.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates CNPJ format and Módulo 11 check-digits.
 *
 * <p>Rules:</p>
 * <ol>
 *   <li>Exactly 14 numeric digits (no punctuation).</li>
 *   <li>Not all digits identical (e.g. "11111111111111" passes Módulo 11 arithmetically but is
 *       invalid by Receita Federal convention).</li>
 *   <li>Check-digits DV1 and DV2 must match the Módulo 11 algorithm.</li>
 * </ol>
 *
 * <p>{@code null} is accepted — use {@code @NotNull}/{@code @NotBlank} for mandatory fields.</p>
 */
public class CnpjValidator implements ConstraintValidator<Cnpj, String> {

    /** Weights for the first check-digit (DV1) calculation (12 digits → remainder). */
    private static final int[] WEIGHTS_DV1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    /** Weights for the second check-digit (DV2) calculation (13 digits → remainder). */
    private static final int[] WEIGHTS_DV2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) {
            // null is accepted; mandatory validation is a separate concern (@NotNull / @ValidRegisterRequest)
            return true;
        }

        // Must be exactly 14 numeric digits
        if (!value.matches("\\d{14}")) {
            return false;
        }

        // Reject CNPJs with all identical digits (pass Módulo 11 arithmetically but are invalid)
        if (value.chars().distinct().count() == 1) {
            return false;
        }

        return checkDigits(value);
    }

    /**
     * Validates both check-digits (DV1 and DV2) using the Módulo 11 algorithm.
     *
     * @param cnpj 14-digit string (already confirmed to match {@code \d{14}})
     * @return {@code true} if both check-digits are correct
     */
    private boolean checkDigits(String cnpj) {
        int dv1 = computeDigit(cnpj, WEIGHTS_DV1);
        if (dv1 != Character.getNumericValue(cnpj.charAt(12))) {
            return false;
        }
        int dv2 = computeDigit(cnpj, WEIGHTS_DV2);
        return dv2 == Character.getNumericValue(cnpj.charAt(13));
    }

    /**
     * Computes a single check-digit given the CNPJ string and a weight array.
     * The weight array length determines how many leading digits are used.
     *
     * @param cnpj    14-digit CNPJ string
     * @param weights weight array ({@code WEIGHTS_DV1} has length 12, {@code WEIGHTS_DV2} has length 13)
     * @return computed check-digit (0–9)
     */
    private int computeDigit(String cnpj, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += Character.getNumericValue(cnpj.charAt(i)) * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
