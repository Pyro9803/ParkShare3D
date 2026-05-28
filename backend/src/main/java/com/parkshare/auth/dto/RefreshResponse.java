package com.parkshare.auth.dto;

public record RefreshResponse(
        String accessToken,
        int expiresIn
) {
}
