package com.parkshare.auth.token;

import java.util.UUID;

public record RefreshToken(
        UUID userId,
        UUID tokenId
) {
}
