package com.cbo.players.service;

import com.cbo.players.dto.response.QuoteRecalculateResponseDto;
import com.cbo.players.event.QuotesRecalculatedEvent;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerQuote;
import com.cbo.players.model.PlayerStatistics;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.PlayerStatisticsRepository;
import com.cbo.players.service.formula.ValuationFormula;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class QuoteCalculationService {

    private static final Logger log = LoggerFactory.getLogger(QuoteCalculationService.class);

    private final ValuationStrategyService valuationStrategyService;
    private final List<ValuationFormula> formulas;
    private final PlayerRepository playerRepository;
    private final PlayerStatisticsRepository playerStatisticsRepository;
    private final PlayerQuoteRepository playerQuoteRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheManager cacheManager;

    public QuoteCalculationService(
            ValuationStrategyService valuationStrategyService,
            List<ValuationFormula> formulas,
            PlayerRepository playerRepository,
            PlayerStatisticsRepository playerStatisticsRepository,
            PlayerQuoteRepository playerQuoteRepository,
            ApplicationEventPublisher eventPublisher,
            @Autowired(required = false) CacheManager cacheManager) {
        this.valuationStrategyService = valuationStrategyService;
        this.formulas = formulas;
        this.playerRepository = playerRepository;
        this.playerStatisticsRepository = playerStatisticsRepository;
        this.playerQuoteRepository = playerQuoteRepository;
        this.eventPublisher = eventPublisher;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public QuoteRecalculateResponseDto recalculateAllQuotes() {
        long startTime = System.currentTimeMillis();
        ValuationStrategy activeStrategy = valuationStrategyService.getActiveStrategy();

        ValuationFormula formula = formulas.stream()
                .filter(f -> f.getStrategyCode().equalsIgnoreCase(activeStrategy.getCode()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        "No se encontró implementación de fórmula para la estrategia: " + activeStrategy.getCode(),
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        ErrorCode.INTERNAL_SERVER_ERROR
                ));

        List<Player> activePlayers = playerRepository.findAll().stream()
                .filter(Player::isActive)
                .toList();

        List<PlayerQuote> createdQuotes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        for (Player player : activePlayers) {
            PlayerStatistics stats = playerStatisticsRepository.findById(player.getId()).orElse(null);
            BigDecimal calculatedPrice = formula.calculate(stats, activeStrategy);

            PlayerQuote quote = new PlayerQuote(
                    player,
                    calculatedPrice,
                    "CRD",
                    now,
                    activeStrategy.getName(),
                    activeStrategy.getVersion()
            );
            createdQuotes.add(quote);
        }

        if (!createdQuotes.isEmpty()) {
            playerQuoteRepository.saveAll(createdQuotes);
        }

        // Publicar evento de dominio para evaluación de alertas en tiempo real (SSE)
        eventPublisher.publishEvent(new QuotesRecalculatedEvent(this, createdQuotes, activeStrategy, now));

        // Invalidar caché Redis
        evictCaches();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Recálculo global completado: {} jugadores procesados con estrategia [{}] v{} en {} ms",
                createdQuotes.size(), activeStrategy.getCode(), activeStrategy.getVersion(), duration);

        return new QuoteRecalculateResponseDto(
                createdQuotes.size(),
                activeStrategy.getCode(),
                activeStrategy.getName(),
                activeStrategy.getVersion(),
                duration,
                now
        );
    }

    private void evictCaches() {
        if (cacheManager != null) {
            try {
                Cache rankingCache = cacheManager.getCache("players::ranking");
                if (rankingCache != null) {
                    rankingCache.clear();
                }
                Cache catalogCache = cacheManager.getCache("players::catalog");
                if (catalogCache != null) {
                    catalogCache.clear();
                }
            } catch (Exception ex) {
                log.warn("No se pudo limpiar la caché durante el recálculo global: {}", ex.getMessage());
            }
        }
    }
}
