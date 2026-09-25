package com.securevaultx.backend.controllers;

import com.securevaultx.backend.request.RegisterRequest;
import com.securevaultx.backend.response.CsrfResponse;
import com.securevaultx.backend.response.UserResponse;
import com.securevaultx.backend.security.AppUserDetails;
import com.securevaultx.backend.services.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/auth/login and POST /api/auth/logout are implemented by Spring Security's filters
 * (see SecurityConfig) and therefore do not appear here.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	/** Returns the CSRF token bound to the caller's session; send it back in the given header on POST/DELETE. */
	@GetMapping("/csrf")
	public CsrfResponse csrf(CsrfToken token) {
		return new CsrfResponse(token.getHeaderName(), token.getToken());
	}

	@PostMapping("/register")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
	}

	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal AppUserDetails principal) {
		return authService.currentUser(principal.getId());
	}
}
