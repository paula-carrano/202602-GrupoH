package com.cbo.players.service;

import com.cbo.players.dto.response.PlayerResponseDto;
import com.cbo.players.dto.response.PlayerDetailResponseDto;
import com.cbo.players.dto.response.PlayerStatisticsResponseDto;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.exception.ResourceNotFoundException;
import com.cbo.players.model.Player;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.PlayerStatisticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerStatisticsRepository playerStatisticsRepository;

    public PlayerService(PlayerRepository playerRepository, PlayerStatisticsRepository playerStatisticsRepository) {
        this.playerRepository = playerRepository;
        this.playerStatisticsRepository = playerStatisticsRepository;
    }

    @Transactional(readOnly = true)
    public List<PlayerResponseDto> getAllPlayers() {
        return playerRepository.findAllByActiveTrue().stream()
                .map(PlayerResponseDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlayerDetailResponseDto getPlayerById(Long id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró ningún jugador con el identificador especificado.",
                        ErrorCode.PLAYER_NOT_FOUND
                ));

        PlayerStatisticsResponseDto statistics = playerStatisticsRepository.findById(id)
                .map(PlayerStatisticsResponseDto::fromEntity)
                .orElse(null);
        return PlayerDetailResponseDto.fromEntity(player, statistics);
    }
}
