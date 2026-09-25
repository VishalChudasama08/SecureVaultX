package com.securevaultx.backend.services;

import com.securevaultx.backend.entities.User;
import com.securevaultx.backend.exception.ApiException;
import com.securevaultx.backend.repositories.UserRepository;
import com.securevaultx.backend.request.RegisterRequest;
import com.securevaultx.backend.response.UserResponse;
import com.securevaultx.backend.util.EmailNormalizer;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Registration and profile lookups. Login/logout are NOT here on purpose: they are handled by Spring
 * Security's own filters (see SecurityConfig), so authentication can never be bypassed by ad-hoc code.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG",
                    "Password must be at most 72 bytes long.");
        }
        String email = EmailNormalizer.normalize(request.email());
        if (users.existsByEmail(email)) {
            throw duplicate();
        }
        User user = new User(request.fullName().strip(), email, passwordEncoder.encode(request.password()));
        try {
            // saveAndFlush so the unique-constraint race (two simultaneous registrations) surfaces right here
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw duplicate();
        }
        log.info("User registered (id={})", user.getId());
        return UserResponse.from(user);
    }

    public UserResponse currentUser(long userId) {
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "Your account no longer exists."));
    }

    private static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                "An account with this email already exists.");
    }
}
