package com.acaboumony.user.controller;

import com.acaboumony.user.dto.request.UpdateProfileRequest;
import com.acaboumony.user.dto.response.UserProfileResponse;
import com.acaboumony.user.security.JwtAuthenticationToken;
import com.acaboumony.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal JwtAuthenticationToken jwt) {
        return userService.getProfile(UUID.fromString(jwt.getName()));
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMe(@AuthenticationPrincipal JwtAuthenticationToken jwt,
                                        @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateFullName(UUID.fromString(jwt.getName()), req.fullName());
    }
}
