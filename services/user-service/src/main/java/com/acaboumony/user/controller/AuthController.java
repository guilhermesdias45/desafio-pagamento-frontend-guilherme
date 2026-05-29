package com.acaboumony.user.controller;

import com.acaboumony.user.dto.request.ConfirmEmailRequest;
import com.acaboumony.user.dto.request.LoginRequest;
import com.acaboumony.user.dto.request.RegisterRequest;
import com.acaboumony.user.dto.response.RegisterResponse;
import com.acaboumony.user.exception.RefreshTokenInvalidException;
import com.acaboumony.user.result.AuthResult;
import com.acaboumony.user.security.JwtAuthenticationToken;
import com.acaboumony.user.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/confirm-email")
    public ResponseEntity<Void> confirmEmail(@Valid @RequestBody ConfirmEmailRequest req) {
        authService.confirmEmail(req.token());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletResponse res) {
        AuthResult result = authService.authenticate(req);
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
            case AuthResult.Failure f -> mapFailure(f);
        };
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = "refreshToken", required = false) String token,
            HttpServletResponse res) {
        if (token == null) {
            throw new RefreshTokenInvalidException();
        }
        var r = authService.refresh(token);
        CookieHelper.setRefreshCookie(res, r.refreshToken());
        return ResponseEntity.ok(Map.of(
                "accessToken", r.accessToken(),
                "tokenType", r.tokenType(),
                "expiresIn", r.expiresIn()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal JwtAuthenticationToken jwt,
            @CookieValue(name = "refreshToken", required = false) String token) {
        if (token != null) {
            authService.logout(UUID.fromString(jwt.getName()), token);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-confirmation")
    public ResponseEntity<ProblemDetail> resendConfirmation() {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_IMPLEMENTED, "Feature available in Sprint 2");
        pd.setType(URI.create("about:blank"));
        pd.setProperty("errorCode", "NOT_IMPLEMENTED_SPRINT_2");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(pd);
    }

    private ResponseEntity<?> mapFailure(AuthResult.Failure f) {
        HttpStatus status = switch (f.errorCode()) {
            case "ACCOUNT_LOCKED" -> HttpStatus.LOCKED;
            case "ACCOUNT_NOT_CONFIRMED", "ACCOUNT_DISABLED" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.UNAUTHORIZED;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, f.message());
        pd.setType(URI.create("about:blank"));
        pd.setProperty("errorCode", f.errorCode());
        pd.setProperty("retryable", f.retryable());
        if (f.unlockAt() != null) {
            pd.setProperty("unlockAt", f.unlockAt());
        }
        return ResponseEntity.status(status)
                .header("Content-Type", "application/problem+json")
                .body(pd);
    }
}
