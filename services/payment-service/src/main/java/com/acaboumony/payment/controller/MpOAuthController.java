package com.acaboumony.payment.controller;

import com.acaboumony.payment.service.MpOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/admin/mp-oauth")
public class MpOAuthController {

    private static final Logger log = LoggerFactory.getLogger(MpOAuthController.class);

    private final MpOAuthService oAuthService;

    public MpOAuthController(MpOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    @GetMapping("/authorize")
    public void authorize(HttpServletResponse response) throws IOException {
        var url = oAuthService.generateAuthorizationUrl();
        log.info("Redirecting to MP OAuth authorize URL");
        response.sendRedirect(url);
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(@RequestParam(required = false) String code,
                                           @RequestParam(required = false) String state) {
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body("Missing required parameter 'code'");
        }
        log.info("Received OAuth callback with code and state={}", state);
        try {
            var tokens = oAuthService.exchangeCode(code);
            log.info("OAuth tokens obtained successfully, expires at {}", tokens.expiresAt());
            return ResponseEntity.ok("MercadoPago account linked successfully. You may close this tab.");
        } catch (Exception e) {
            log.error("OAuth callback failed", e);
            return ResponseEntity.internalServerError()
                .body("Failed to link MercadoPago account: " + e.getMessage());
        }
    }
}
