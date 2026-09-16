package com.cbo.players.service;

import com.cbo.players.dto.response.PlayerResponseDto;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.exception.ResourceNotFoundException;
import com.cbo.players.model.Player;
import com.cbo.players.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Transactional(readOnly = true)
    public List<PlayerResponseDto> getAllPlayers() {
        return playerRepository.findAllByActiveTrue().stream()
                .map(PlayerResponseDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlayerResponseDto getPlayerById(Long id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró ningún jugador con el identificador especificado.",
                        ErrorCode.PLAYER_NOT_FOUND
                ));

        return PlayerResponseDto.fromEntity(player);
    }
}
