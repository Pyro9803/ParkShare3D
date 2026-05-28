package com.parkshare.auth;

import java.util.UUID;

import com.parkshare.auth.dto.LoginRequest;
import com.parkshare.auth.dto.LoginResponse;
import com.parkshare.auth.dto.MeResponse;
import com.parkshare.auth.dto.RefreshRequest;
import com.parkshare.auth.dto.RefreshResponse;
import com.parkshare.auth.dto.RegisterRequest;
import com.parkshare.auth.dto.RegisterResponse;
import com.parkshare.auth.jwt.JwtProperties;
import com.parkshare.auth.jwt.JwtProvider;
import com.parkshare.auth.token.RefreshToken;
import com.parkshare.auth.token.RefreshTokenService;
import com.parkshare.shared.exception.BusinessException;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.shared.exception.InvalidCredentialsException;
import com.parkshare.shared.exception.InvalidRefreshTokenException;
import com.parkshare.user.User;
import com.parkshare.user.UserRepository;
import com.parkshare.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            RefreshTokenService refreshTokenService,
            JwtProperties jwtProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (request.role() == UserRole.ADMIN) {
            throw new BusinessException("INVALID_REGISTRATION_ROLE", "Admin users cannot self-register");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "Email already exists");
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phone(request.phone())
                .role(request.role())
                .active(true)
                .build();
        User savedUser = userRepository.save(user);

        return new RegisterResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getRole());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = refreshTokenService.issue(user.getId());
        return new LoginResponse(accessToken, refreshToken, accessTokenTtlSeconds());
    }

    @Transactional
    public RefreshResponse refresh(RefreshRequest request) {
        RefreshToken refreshToken = refreshTokenService.validate(request.refreshToken());
        UUID userId = refreshToken.userId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("USER_NOT_FOUND", "User not found"));
        UUID newTokenId = UUID.randomUUID();
        refreshTokenService.rotate(userId, refreshToken.tokenId(), newTokenId);
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        return new RefreshResponse(accessToken, accessTokenTtlSeconds());
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("USER_NOT_FOUND", "User not found"));
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.isActive()
        );
    }

    public void logout(UUID userId, RefreshRequest request) {
        RefreshToken refreshToken = refreshTokenService.parse(request.refreshToken());
        if (!refreshToken.userId().equals(userId)) {
            throw new InvalidRefreshTokenException();
        }
        refreshTokenService.revoke(userId, refreshToken.tokenId());
    }

    private int accessTokenTtlSeconds() {
        return Math.toIntExact(jwtProperties.accessTokenTtlSeconds());
    }
}
