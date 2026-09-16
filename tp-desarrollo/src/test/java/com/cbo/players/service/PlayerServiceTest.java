package com.cbo.players.service;

import com.cbo.players.dto.response.PlayerResponseDto;
import com.cbo.players.exception.ResourceNotFoundException;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        playerService = new PlayerService(playerRepository);
    }

    @Test
    void getAllPlayers_returnsList() {
        Player player1 = new Player("Lionel", "Messi", LocalDate.of(1987, 6, 24), "Argentina", PlayerPosition.FORWARD, "Inter Miami", "MLS");
        player1.setId(1L);
        Player player2 = new Player("Juvenil", "Promesa", null, "Argentina", PlayerPosition.MIDFIELDER, "Banfield", "Liga Profesional");
        player2.setId(2L);

        when(playerRepository.findAllByActiveTrue()).thenReturn(List.of(player1, player2));

        List<PlayerResponseDto> result = playerService.getAllPlayers();

        assertEquals(2, result.size());
        assertEquals("Lionel", result.get(0).firstName());
        assertNull(result.get(1).birthDate());
    }

    @Test
    void getPlayerById_existingId_returnsPlayer() {
        Player player = new Player("Lionel", "Messi", LocalDate.of(1987, 6, 24), "Argentina", PlayerPosition.FORWARD, "Inter Miami", "MLS");
        player.setId(1L);

        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

        PlayerResponseDto result = playerService.getPlayerById(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("Messi", result.lastName());
    }

    @Test
    void getPlayerById_notFound_throwsException() {
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> playerService.getPlayerById(999L));
    }
}
