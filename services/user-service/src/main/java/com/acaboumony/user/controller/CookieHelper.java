package com.acaboumony.user.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/** Shared helper for setting the HttpOnly refresh token cookie. */
final class CookieHelper {

    private CookieHelper() {}

    static void setRefreshCookie(HttpServletResponse res, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(604800)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
