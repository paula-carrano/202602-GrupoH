package com.cbo.players.controller;

import com.cbo.players.dto.response.PlayerResponseDto;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.exception.GlobalExceptionHandler;
import com.cbo.players.exception.ResourceNotFoundException;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PlayerControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PlayerService playerService;

    @InjectMocks
    private PlayerController playerController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(playerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllPlayers_returnsList() throws Exception {
        PlayerResponseDto player1 = new PlayerResponseDto(1L, "Lionel", "Messi", LocalDate.of(1987, 6, 24),
                "Argentina", PlayerPosition.FORWARD, "Inter Miami", "MLS", true);
        PlayerResponseDto player2 = new PlayerResponseDto(2L, "Juvenil", "Promesa", null,
                "Argentina", PlayerPosition.MIDFIELDER, "Banfield", "Liga Profesional", true);

        when(playerService.getAllPlayers()).thenReturn(List.of(player1, player2));

        mockMvc.perform(get("/api/v1/players")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].firstName").value("Lionel"))
                .andExpect(jsonPath("$[1].birthDate").doesNotExist()); // or null in json
    }

    @Test
    void getPlayerById_existingId_returns200() throws Exception {
        PlayerResponseDto player = new PlayerResponseDto(1L, "Lionel", "Messi", LocalDate.of(1987, 6, 24),
                "Argentina", PlayerPosition.FORWARD, "Inter Miami", "MLS", true);

        when(playerService.getPlayerById(1L)).thenReturn(player);

        mockMvc.perform(get("/api/v1/players/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.lastName").value("Messi"));
    }

    @Test
    void getPlayerById_notFound_returns404WithProblemDetail() throws Exception {
        when(playerService.getPlayerById(999L)).thenThrow(
                new ResourceNotFoundException("No se encontró ningún jugador con el identificador especificado.", ErrorCode.PLAYER_NOT_FOUND)
        );

        mockMvc.perform(get("/api/v1/players/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("PLAYER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("No se encontró ningún jugador con el identificador especificado."));
    }
}
