package com.securevaultx.backend.response;

import com.securevaultx.backend.entities.User;
import java.time.Instant;

public record UserResponse(String fullName, String email, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getFullName(), user.getEmail(), user.getCreatedAt());
    }
}
