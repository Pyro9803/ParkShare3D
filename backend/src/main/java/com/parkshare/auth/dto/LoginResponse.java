package com.parkshare.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        int expiresIn
) {
}
