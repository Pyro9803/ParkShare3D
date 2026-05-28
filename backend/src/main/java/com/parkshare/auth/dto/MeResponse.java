package com.parkshare.auth.dto;

import java.util.UUID;

import com.parkshare.user.UserRole;

public record MeResponse(
        UUID userId,
        String email,
        String fullName,
        String phone,
        UserRole role,
        boolean active
) {
}
