package com.cbo.players.service;

import com.cbo.players.adapter.ScrapingException;
import com.cbo.players.adapter.ScrapingRestAdapter;
import com.cbo.players.adapter.dto.CatalogTeamDto;
import com.cbo.players.adapter.dto.PlayerStatsDto;
import com.cbo.players.adapter.dto.SquadPlayerDto;
import com.cbo.players.adapter.dto.TeamSquadDto;
import com.cbo.players.config.RedisCacheConfig;
import com.cbo.players.dto.response.PlayerDetailResponseDto;
import com.cbo.players.dto.response.PlayerResponseDto;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.PlayerStatisticsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class PlayerServiceCacheTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerStatisticsRepository playerStatisticsRepository;

    private Player samplePlayer;

    @BeforeEach
    void setUp() {
        samplePlayer = new Player("Lionel", "Messi", LocalDate.of(1987, 6, 24),
                "Argentine", PlayerPosition.FORWARD, "Inter Miami", "MLS");
        samplePlayer.setId(10L);
    }

    @Test
    @DisplayName("FR-002 - PlayerService cachea catálogo y detalle en players::catalog")
    void playerService_cachesCatalogAndDetail() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(RedisCacheConfig.CACHE_CATALOG);
        PlayerService service = new PlayerService(playerRepository, playerStatisticsRepository, cacheManager);

        when(playerRepository.findAllByActiveTrue()).thenReturn(List.of(samplePlayer));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(samplePlayer));
        when(playerStatisticsRepository.findById(10L)).thenReturn(Optional.empty());

        List<PlayerResponseDto> firstList = service.getAllPlayers();
        List<PlayerResponseDto> secondList = service.getAllPlayers();
        assertThat(firstList).hasSize(1);
        assertThat(secondList).hasSize(1);
        verify(playerRepository, times(1)).findAllByActiveTrue();

        PlayerDetailResponseDto firstDetail = service.getPlayerById(10L);
        PlayerDetailResponseDto secondDetail = service.getPlayerById(10L);
        assertThat(firstDetail.firstName()).isEqualTo("Lionel");
        assertThat(secondDetail.firstName()).isEqualTo("Lionel");
        verify(playerRepository, times(1)).findById(10L);
    }

    @Test
    @DisplayName("SC-006 - PlayerService degrada a PostgreSQL si Redis falla")
    void playerService_fallsBackWhenRedisUnavailable() {
        CacheManager failingCacheManager = mock(CacheManager.class);
        Cache failingCache = mock(Cache.class);
        when(failingCacheManager.getCache(RedisCacheConfig.CACHE_CATALOG)).thenReturn(failingCache);
        when(failingCache.get(any(), any(Class.class))).thenThrow(new RuntimeException("Redis timeout"));

        PlayerService service = new PlayerService(playerRepository, playerStatisticsRepository, failingCacheManager);
        when(playerRepository.findAllByActiveTrue()).thenReturn(List.of(samplePlayer));

        List<PlayerResponseDto> result = service.getAllPlayers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).firstName()).isEqualTo("Lionel");
    }

    @Test
    @DisplayName("FR-002 - CatalogPersistenceService invalida players::catalog y players::ranking al importar")
    void catalogPersistence_evictsCatalogAndRankingOnImport() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                RedisCacheConfig.CACHE_CATALOG, RedisCacheConfig.CACHE_RANKING);
        cacheManager.getCache(RedisCacheConfig.CACHE_CATALOG).put(PlayerService.ALL_PLAYERS_CACHE_KEY, List.of());
        cacheManager.getCache(RedisCacheConfig.CACHE_RANKING).put("active_ranking:BALANCED:v1", "cached");

        CatalogPersistenceService persistence = new CatalogPersistenceService(
                playerRepository, playerStatisticsRepository, cacheManager);

        when(playerRepository.findByFootballDataId(77L)).thenReturn(Optional.empty());
        TeamSquadDto squad = new TeamSquadDto(
                new CatalogTeamDto(1L, "Arsenal"),
                List.of(new SquadPlayerDto(77L, "Bukayo Saka", "Bukayo", "Saka", LocalDate.of(2001, 9, 5), "England", "Forward"))
        );

        CatalogPersistenceService.ImportResult result = persistence.importSquad("PL", squad);

        assertThat(result.created()).isEqualTo(1);
        assertThat(cacheManager.getCache(RedisCacheConfig.CACHE_CATALOG).get(PlayerService.ALL_PLAYERS_CACHE_KEY)).isNull();
        assertThat(cacheManager.getCache(RedisCacheConfig.CACHE_RANKING).get("active_ranking:BALANCED:v1")).isNull();
    }

    @Test
    @DisplayName("FR-002 - ScrapingRestAdapter cachea respuestas externas y hace fallback ante caída 503 del proveedor")
    void scrapingRestAdapter_cachesExternalResponseAndFallsBackOn5xx() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://scraper.local");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(ScrapingRestAdapter.CACHE_EXTERNAL);

        ScrapingRestAdapter adapter = new ScrapingRestAdapter(
                builder.build(), new ObjectMapper(), "test-key", cacheManager);

        String statsJson = """
                {"goals":25,"assists":10,"shots":80,"keyPasses":45,"dribbles":55,"tackles":8,"rating":8.12,"minutosJugados":2700,"tarjetasAmarillas":3,"tarjetasRojas":0}
                """;
        server.expect(requestTo("http://scraper.local/players/300713/stats"))
                .andRespond(withSuccess(statsJson, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://scraper.local/players/300713/stats"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"SCRAPE_TIMEOUT\"}"));
        server.expect(requestTo("http://scraper.local/players/999999/stats"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"PLAYER_NOT_FOUND\"}"));

        PlayerStatsDto fresh = adapter.getPlayerStats(300713L);
        assertThat(fresh.goals()).isEqualTo(25);
        assertThat(fresh.rating()).isEqualTo(8.12);

        // Segunda llamada: proveedor devuelve 503 -> sirve desde caché Redis externa
        PlayerStatsDto fallback = adapter.getPlayerStats(300713L);
        assertThat(fallback.goals()).isEqualTo(25);

        // 404 sin caché previa sigue lanzando ScrapingException
        assertThatThrownBy(() -> adapter.getPlayerStats(999999L))
                .isInstanceOf(ScrapingException.class);
        server.verify();
    }
}
