package com.parkshare.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtProvider,
                refreshTokenService,
                new JwtProperties("this-is-a-256-bit-test-secret-key-123456", 900, 604800)
        );
    }

    @Test
    void registerCreatesUserWithHashedPassword() {
        UUID userId = UUID.randomUUID();
        RegisterRequest request = new RegisterRequest(
                "driver@example.com",
                "password123",
                "Driver User",
                "555-0100",
                UserRole.DRIVER
        );
        User savedUser = User.builder()
                .id(userId)
                .email(request.email())
                .password("hashed-password")
                .fullName(request.fullName())
                .phone(request.phone())
                .role(request.role())
                .active(true)
                .build();
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        RegisterResponse response = authService.register(request);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.role()).isEqualTo(UserRole.DRIVER);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("hashed-password");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "driver@example.com",
                "password123",
                "Driver User",
                null,
                UserRole.DRIVER
        );
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOfSatisfying(ConflictException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsAdminSelfRegistration() {
        RegisterRequest request = new RegisterRequest(
                "admin@example.com",
                "password123",
                "Admin User",
                null,
                UserRole.ADMIN
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_REGISTRATION_ROLE");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsAccessAndRefreshTokens() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(userRepository.findByEmail("driver@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", user.getPassword())).thenReturn(true);
        when(jwtProvider.generateAccessToken(userId, user.getEmail(), user.getRole())).thenReturn("access-token");
        when(refreshTokenService.issue(userId)).thenReturn("refresh-token");

        LoginResponse response = authService.login(new LoginRequest("driver@example.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", "password123")))
                .isInstanceOfSatisfying(InvalidCredentialsException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_CREDENTIALS");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = user(UUID.randomUUID());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsInactiveUserWithInvalidCredentials() {
        User user = user(UUID.randomUUID());
        user.setActive(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwtProvider, never()).generateAccessToken(any(), any(), any());
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void refreshRotatesRefreshTokenAndReturnsNewAccessToken() {
        UUID userId = UUID.randomUUID();
        UUID oldTokenId = UUID.randomUUID();
        String rawRefreshToken = userId + ":" + oldTokenId;
        User user = user(userId);
        when(refreshTokenService.validate(rawRefreshToken)).thenReturn(new RefreshToken(userId, oldTokenId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtProvider.generateAccessToken(userId, user.getEmail(), user.getRole())).thenReturn("new-access-token");

        RefreshResponse response = authService.refresh(new RefreshRequest(rawRefreshToken));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.expiresIn()).isEqualTo(900);
        verify(refreshTokenService).rotate(eq(userId), eq(oldTokenId), any(UUID.class));
        verify(refreshTokenService, never()).extractTokenId(rawRefreshToken);
        InOrder inOrder = inOrder(refreshTokenService, userRepository);
        inOrder.verify(refreshTokenService).validate(rawRefreshToken);
        inOrder.verify(userRepository).findById(userId);
    }

    @Test
    void refreshRejectsInvalidRefreshToken() {
        RefreshRequest request = new RefreshRequest("invalid-token");
        when(refreshTokenService.validate(request.refreshToken())).thenThrow(new InvalidRefreshTokenException());

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOfSatisfying(InvalidRefreshTokenException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_REFRESH_TOKEN");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    void meReturnsCurrentUserDetails() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MeResponse response = authService.me(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo(user.getEmail());
        assertThat(response.fullName()).isEqualTo(user.getFullName());
        assertThat(response.phone()).isEqualTo(user.getPhone());
        assertThat(response.role()).isEqualTo(user.getRole());
        assertThat(response.active()).isTrue();
    }

    @Test
    void meRejectsMissingUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.me(userId))
                .isInstanceOfSatisfying(EntityNotFoundException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("USER_NOT_FOUND"));
    }

    @Test
    void logoutRevokesRefreshTokenForCurrentUser() {
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String rawRefreshToken = userId + ":" + tokenId;
        when(refreshTokenService.parse(rawRefreshToken)).thenReturn(new RefreshToken(userId, tokenId));

        authService.logout(userId, new RefreshRequest(rawRefreshToken));

        verify(refreshTokenService).revoke(userId, tokenId);
    }

    @Test
    void logoutRejectsRefreshTokenForDifferentUser() {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID tokenUserId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String rawRefreshToken = tokenUserId + ":" + tokenId;
        when(refreshTokenService.parse(rawRefreshToken)).thenReturn(new RefreshToken(tokenUserId, tokenId));

        assertThatThrownBy(() -> authService.logout(authenticatedUserId, new RefreshRequest(rawRefreshToken)))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenService, never()).revoke(any(), any());
    }

    private static User user(UUID userId) {
        return User.builder()
                .id(userId)
                .email("driver@example.com")
                .password("hashed-password")
                .fullName("Driver User")
                .phone("555-0100")
                .role(UserRole.DRIVER)
                .active(true)
                .build();
    }
}
