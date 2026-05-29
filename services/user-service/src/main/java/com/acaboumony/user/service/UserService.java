package com.acaboumony.user.service;

import com.acaboumony.user.domain.entity.User;
import com.acaboumony.user.dto.response.UserProfileResponse;
import com.acaboumony.user.exception.UserNotFoundException;
import com.acaboumony.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        return toResponse(user);
    }

    @Transactional
    public UserProfileResponse updateFullName(UUID userId, String fullName) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.setFullName(fullName);
        return toResponse(userRepository.save(user));
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getMerchant() != null ? user.getMerchant().getId() : null,
                user.isTotpEnabled(),
                user.getCreatedAt());
    }
}
