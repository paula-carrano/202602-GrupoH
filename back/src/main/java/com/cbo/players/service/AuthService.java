package com.cbo.players.service;

import com.cbo.players.dto.request.LoginRequestDto;
import com.cbo.players.dto.request.RegisterRequestDto;
import com.cbo.players.dto.response.LoginResponseDto;
import com.cbo.players.dto.response.UserResponseDto;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.exception.InvalidCredentialsException;
import com.cbo.players.exception.UserAlreadyExistsException;
import com.cbo.players.model.User;
import com.cbo.players.model.UserRole;
import com.cbo.players.repository.UserRepository;
import com.cbo.players.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public UserResponseDto register(RegisterRequestDto request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException("El nombre de usuario ya se encuentra registrado.", ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("El correo electrónico ya se encuentra registrado.", ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                UserRole.ROLE_USER
        );

        User savedUser = userRepository.save(user);

        return new UserResponseDto(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getCreatedAt()
        );
    }

    public LoginResponseDto login(LoginRequestDto request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            String token = tokenProvider.generateToken(authentication);

            return new LoginResponseDto(
                    token,
                    "Bearer",
                    tokenProvider.getExpirationTimeSeconds()
            );
        } catch (BadCredentialsException | InvalidCredentialsException ex) {
            throw new InvalidCredentialsException("Credenciales inválidas. Verifique su usuario y contraseña.");
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException("Error de autenticación. Verifique sus credenciales.");
        }
    }
}
