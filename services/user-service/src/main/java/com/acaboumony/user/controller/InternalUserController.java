package com.acaboumony.user.controller;

import com.acaboumony.user.dto.response.InternalUserResponse;
import com.acaboumony.user.exception.UserNotFoundException;
import com.acaboumony.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal endpoint used by payment-service to validate customer existence.
 *
 * <p>Route: {@code GET /internal/users/{customerId}}</p>
 * Protected by {@link com.acaboumony.user.security.InternalSecretFilter}.
 * Returns only non-sensitive fields: id, email, role.
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private static final Logger log = LoggerFactory.getLogger(InternalUserController.class);

    private final UserRepository userRepository;

    public InternalUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<InternalUserResponse> getUser(@PathVariable UUID customerId) {
        var user = userRepository.findById(customerId)
                .orElseThrow(UserNotFoundException::new);

        log.debug("Internal user lookup: customerId={}, role={}", customerId, user.getRole());

        return ResponseEntity.ok(new InternalUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        ));
    }
}
