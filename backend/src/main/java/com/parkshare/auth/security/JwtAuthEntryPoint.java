package com.parkshare.auth.security;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkshare.shared.api.ApiError;
import com.parkshare.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        boolean tokenExpired = Boolean.TRUE.equals(request.getAttribute("tokenExpired"));
        ApiError error = tokenExpired
                ? new ApiError("TOKEN_EXPIRED", "Token expired")
                : new ApiError("UNAUTHORIZED", "Authentication required");

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(error));
    }
}
