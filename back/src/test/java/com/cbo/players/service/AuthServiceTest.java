package com.cbo.players.service;

import com.cbo.players.dto.request.LoginRequestDto;
import com.cbo.players.dto.request.RegisterRequestDto;
import com.cbo.players.dto.response.LoginResponseDto;
import com.cbo.players.dto.response.UserResponseDto;
import com.cbo.players.exception.InvalidCredentialsException;
import com.cbo.players.exception.UserAlreadyExistsException;
import com.cbo.players.model.User;
import com.cbo.players.model.UserRole;
import com.cbo.players.repository.UserRepository;
import com.cbo.players.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, tokenProvider);
    }

    @Test
    void register_success() {
        RegisterRequestDto request = new RegisterRequestDto("usuario123", "usuario@ejemplo.com", "Password123!");

        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");

        User savedUser = new User(request.username(), request.email(), "encodedPassword", UserRole.ROLE_USER);
        savedUser.setId(1L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponseDto response = authService.register(request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("usuario123", response.username());
        assertEquals("usuario@ejemplo.com", response.email());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_usernameAlreadyExists_throwsException() {
        RegisterRequestDto request = new RegisterRequestDto("usuario123", "usuario@ejemplo.com", "Password123!");

        when(userRepository.existsByUsername(request.username())).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_emailAlreadyExists_throwsException() {
        RegisterRequestDto request = new RegisterRequestDto("usuario123", "usuario@ejemplo.com", "Password123!");

        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_success() {
        LoginRequestDto request = new LoginRequestDto("usuario123", "Password123!");
        Authentication auth = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("mocked.jwt.token");
        when(tokenProvider.getExpirationTimeSeconds()).thenReturn(86400L);

        LoginResponseDto response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked.jwt.token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(86400L, response.expiresInSeconds());
    }

    @Test
    void login_badCredentials_throwsInvalidCredentialsException() {
        LoginRequestDto request = new LoginRequestDto("usuario123", "WrongPassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }
}
