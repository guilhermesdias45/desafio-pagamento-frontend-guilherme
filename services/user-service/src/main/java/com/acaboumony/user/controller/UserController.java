package com.acaboumony.user.controller;

import com.acaboumony.user.dto.request.UpdateProfileRequest;
import com.acaboumony.user.dto.response.UserProfileResponse;
import com.acaboumony.user.security.JwtClaims;
import com.acaboumony.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal JwtClaims claims) {
        return userService.getProfile(claims.sub());
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMe(@AuthenticationPrincipal JwtClaims claims,
                                        @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateFullName(claims.sub(), req.fullName());
    }
}
