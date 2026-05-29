package com.acaboumony.user.controller;

import com.acaboumony.user.dto.request.RecoveryCodeRequest;
import com.acaboumony.user.dto.request.TwoFactorConfirmRequest;
import com.acaboumony.user.dto.request.TwoFactorDisableRequest;
import com.acaboumony.user.dto.request.TwoFactorVerifyRequest;
import com.acaboumony.user.dto.response.TwoFactorSetupResponse;
import com.acaboumony.user.result.AuthResult;
import com.acaboumony.user.security.JwtAuthenticationToken;
import com.acaboumony.user.service.TwoFactorService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@RestController
@RequestMapping("/api/v1/auth/2fa")
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    public TwoFactorController(TwoFactorService twoFactorService) {
        this.twoFactorService = twoFactorService;
    }

    @PostMapping("/setup")
    public TwoFactorSetupResponse setup(@AuthenticationPrincipal JwtAuthenticationToken jwt) {
        return twoFactorService.setup(UUID.fromString(jwt.getName()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@AuthenticationPrincipal JwtAuthenticationToken jwt,
                                        @Valid @RequestBody TwoFactorConfirmRequest req) {
        twoFactorService.confirm(UUID.fromString(jwt.getName()), req.code());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@Valid @RequestBody TwoFactorVerifyRequest req,
                                    HttpServletResponse res) {
        AuthResult result = twoFactorService.verifyTwoFactorToken(req.twoFactorToken(), req.code());
        return mapAuthResult(result, res);
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal JwtAuthenticationToken jwt,
                                        @Valid @RequestBody TwoFactorDisableRequest req) {
        twoFactorService.disable(UUID.fromString(jwt.getName()), req.password(), req.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recovery")
    public ResponseEntity<?> recovery(@Valid @RequestBody RecoveryCodeRequest req,
                                      HttpServletResponse res) {
        AuthResult result = twoFactorService.useRecoveryCodeAndLogin(req.twoFactorToken(), req.code());
        return mapAuthResult(result, res);
    }

    private ResponseEntity<?> mapAuthResult(AuthResult result, HttpServletResponse res) {
        return switch (result) {
            case AuthResult.Success s -> {
                CookieHelper.setRefreshCookie(res, s.refreshToken());
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("accessToken", s.accessToken());
                body.put("tokenType", s.tokenType());
                body.put("expiresIn", s.expiresIn());
                body.put("requiresTwoFactor", false);
                yield ResponseEntity.ok(body);
            }
            case AuthResult.RequiresTwoFactor r -> ResponseEntity.ok(r);
            case AuthResult.Failure f -> {
                HttpStatus status = "ACCOUNT_LOCKED".equals(f.errorCode())
                        ? HttpStatus.LOCKED : HttpStatus.UNAUTHORIZED;
                ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, f.message());
                pd.setType(URI.create("about:blank"));
                pd.setProperty("errorCode", f.errorCode());
                pd.setProperty("retryable", f.retryable());
                yield ResponseEntity.status(status)
                        .header("Content-Type", "application/problem+json")
                        .body(pd);
            }
        };
    }
}
