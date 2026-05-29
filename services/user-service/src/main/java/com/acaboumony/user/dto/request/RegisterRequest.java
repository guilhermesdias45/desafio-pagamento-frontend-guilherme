package com.acaboumony.user.dto.request;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.validation.Cnpj;
import com.acaboumony.user.validation.ValidRegisterRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/register}.
 *
 * <p>Class-level {@code @ValidRegisterRequest} enforces conditional logic:
 * {@code MERCHANT_OWNER} requires {@code companyName} + {@code cnpj};
 * {@code STAFF} is always rejected.</p>
 *
 * <p>{@code toString()} omits {@code password} and {@code cnpj} to prevent accidental log
 * leakage.</p>
 */
@ValidRegisterRequest
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotNull UserRole role,
        @Size(max = 100) String companyName,
        @Cnpj String cnpj
) {

    /**
     * Custom {@code toString()} that excludes {@code password} and {@code cnpj}
     * to prevent sensitive data leaking into logs.
     */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email +
               ", fullName=" + fullName +
               ", role=" + role +
               ", companyName=" + companyName +
               "]";
    }
}
