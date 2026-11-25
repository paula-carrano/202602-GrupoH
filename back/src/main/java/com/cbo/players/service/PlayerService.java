package com.cbo.players.service;

import com.cbo.players.config.RedisCacheConfig;
import com.cbo.players.dto.response.PlayerDetailResponseDto;
import com.cbo.players.dto.response.PlayerResponseDto;
import com.cbo.players.dto.response.PlayerStatisticsResponseDto;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.exception.ResourceNotFoundException;
import com.cbo.players.model.Player;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.PlayerStatisticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlayerService {

    public static final String ALL_PLAYERS_CACHE_KEY = "all_players";

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    private final PlayerRepository playerRepository;
    private final PlayerStatisticsRepository playerStatisticsRepository;
    private final CacheManager cacheManager;

    public PlayerService(PlayerRepository playerRepository, PlayerStatisticsRepository playerStatisticsRepository) {
        this(playerRepository, playerStatisticsRepository, null);
    }

    @Autowired
    public PlayerService(
            PlayerRepository playerRepository,
            PlayerStatisticsRepository playerStatisticsRepository,
            @Autowired(required = false) CacheManager cacheManager) {
        this.playerRepository = playerRepository;
        this.playerStatisticsRepository = playerStatisticsRepository;
        this.cacheManager = cacheManager;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<PlayerResponseDto> getAllPlayers() {
        List<PlayerResponseDto> cached = readCatalogCache(ALL_PLAYERS_CACHE_KEY, List.class);
        if (cached != null) {
            return cached;
        }

        List<PlayerResponseDto> players = playerRepository.findAllByActiveTrue().stream()
                .map(PlayerResponseDto::fromEntity)
                .toList();
        writeCatalogCache(ALL_PLAYERS_CACHE_KEY, players);
        return players;
    }

    @Transactional(readOnly = true)
    public PlayerDetailResponseDto getPlayerById(Long id) {
        String cacheKey = "player:" + id;
        PlayerDetailResponseDto cached = readCatalogCache(cacheKey, PlayerDetailResponseDto.class);
        if (cached != null) {
            return cached;
        }

        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró ningún jugador con el identificador especificado.",
                        ErrorCode.PLAYER_NOT_FOUND
                ));

        PlayerStatisticsResponseDto statistics = playerStatisticsRepository.findById(id)
                .map(PlayerStatisticsResponseDto::fromEntity)
                .orElse(null);
        PlayerDetailResponseDto detail = PlayerDetailResponseDto.fromEntity(player, statistics);
        writeCatalogCache(cacheKey, detail);
        return detail;
    }

    private <T> T readCatalogCache(String key, Class<T> type) {
        if (cacheManager == null) {
            return null;
        }
        try {
            Cache cache = cacheManager.getCache(RedisCacheConfig.CACHE_CATALOG);
            return cache != null ? cache.get(key, type) : null;
        } catch (RuntimeException ex) {
            log.warn("No se pudo leer el catálogo desde caché Redis (key={}). Degradando a PostgreSQL: {}",
                    key, ex.getMessage());
            return null;
        }
    }

    private void writeCatalogCache(String key, Object value) {
        if (cacheManager == null || value == null) {
            return;
        }
        try {
            Cache cache = cacheManager.getCache(RedisCacheConfig.CACHE_CATALOG);
            if (cache != null) {
                cache.put(key, value);
            }
        } catch (RuntimeException ex) {
            log.warn("No se pudo escribir el catálogo en caché Redis (key={}). Continuando sin caché: {}",
                    key, ex.getMessage());
        }
    }
}
