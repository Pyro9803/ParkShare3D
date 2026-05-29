package com.parkshare.admin.dto;

import java.time.Instant;
import java.util.UUID;

import com.parkshare.user.UserRole;

public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserRole role,
        boolean active,
        Instant createdAt
) {
}
