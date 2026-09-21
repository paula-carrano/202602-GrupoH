package com.cbo.players.controller;

import com.cbo.players.dto.request.LoginRequestDto;
import com.cbo.players.dto.request.RegisterRequestDto;
import com.cbo.players.dto.response.LoginResponseDto;
import com.cbo.players.dto.response.UserResponseDto;
import com.cbo.players.exception.GlobalExceptionHandler;
import com.cbo.players.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void register_validPayload_returns201() throws Exception {
        RegisterRequestDto request = new RegisterRequestDto("usuario123", "usuario@ejemplo.com", "Password123!");
        UserResponseDto response = new UserResponseDto(1L, "usuario123", "usuario@ejemplo.com", LocalDateTime.now());

        when(authService.register(any(RegisterRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("usuario123"))
                .andExpect(jsonPath("$.email").value("usuario@ejemplo.com"));
    }

    @Test
    void register_invalidPayload_returns400WithProblemDetail() throws Exception {
        RegisterRequestDto request = new RegisterRequestDto("", "email-invalido", "corta");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void login_validPayload_returns200() throws Exception {
        LoginRequestDto request = new LoginRequestDto("usuario123", "Password123!");
        LoginResponseDto response = new LoginResponseDto("token.jwt.mock", "Bearer", 86400L);

        when(authService.login(any(LoginRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token.jwt.mock"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(86400));
    }
}
