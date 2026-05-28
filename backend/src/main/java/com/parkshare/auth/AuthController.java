package com.parkshare.auth;

import java.util.UUID;

import com.parkshare.auth.dto.LoginRequest;
import com.parkshare.auth.dto.LoginResponse;
import com.parkshare.auth.dto.MeResponse;
import com.parkshare.auth.dto.RefreshRequest;
import com.parkshare.auth.dto.RefreshResponse;
import com.parkshare.auth.dto.RegisterRequest;
import com.parkshare.auth.dto.RegisterResponse;
import com.parkshare.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.register(request)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.success(authService.me(userId));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication, @Valid @RequestBody RefreshRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        authService.logout(userId, request);
        return ApiResponse.success(null);
    }
}
