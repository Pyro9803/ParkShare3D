package com.parkshare.auth.dto;

import java.util.UUID;

import com.parkshare.user.UserRole;

public record RegisterResponse(
        UUID userId,
        String email,
        UserRole role
) {
}
