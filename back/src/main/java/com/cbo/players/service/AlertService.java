package com.cbo.players.service;

import com.cbo.players.dto.request.CreateAlertRequestDto;
import com.cbo.players.dto.response.PriceAlertEventDto;
import com.cbo.players.dto.response.PriceAlertResponseDto;
import com.cbo.players.event.QuotesRecalculatedEvent;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.*;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.PriceAlertRepository;
import com.cbo.players.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final PriceAlertRepository priceAlertRepository;
    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final AlertStreamService alertStreamService;

    public AlertService(PriceAlertRepository priceAlertRepository,
                        UserRepository userRepository,
                        PlayerRepository playerRepository,
                        AlertStreamService alertStreamService) {
        this.priceAlertRepository = priceAlertRepository;
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.alertStreamService = alertStreamService;
    }

    @Transactional
    public PriceAlertResponseDto createAlert(Long userId, CreateAlertRequestDto request) {
        if (request.targetPrice() == null || request.targetPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException("El precio objetivo debe ser mayor a cero", HttpStatus.BAD_REQUEST, ErrorCode.INVALID_AMOUNT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("Usuario no encontrado con ID: " + userId, HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND));

        Player player = playerRepository.findById(request.playerId())
                .filter(Player::isActive)
                .orElseThrow(() -> new ApiException("Jugador no encontrado con ID: " + request.playerId(), HttpStatus.NOT_FOUND, ErrorCode.PLAYER_NOT_FOUND));

        PriceAlert alert = new PriceAlert(user, player, request.condition(), request.targetPrice());
        PriceAlert saved = priceAlertRepository.save(alert);

        return PriceAlertResponseDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PriceAlertResponseDto> getUserAlerts(Long userId) {
        return priceAlertRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(PriceAlertResponseDto::from)
                .toList();
    }

    @Transactional
    public void deleteAlert(Long alertId, Long userId, boolean isPrivileged) {
        PriceAlert alert = priceAlertRepository.findById(alertId)
                .orElseThrow(() -> new ApiException("Alerta no encontrada con ID: " + alertId, HttpStatus.NOT_FOUND, ErrorCode.ALERT_NOT_FOUND));

        if (!alert.getUser().getId().equals(userId) && !isPrivileged) {
            throw new ApiException("Acceso denegado a la alerta solicitada", HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED);
        }

        priceAlertRepository.delete(alert);
    }

    @EventListener
    @Transactional
    public void onQuotesRecalculated(QuotesRecalculatedEvent event) {
        if (event.getQuotes() == null || event.getQuotes().isEmpty()) {
            return;
        }

        List<PriceAlert> activeAlerts = priceAlertRepository.findByStatus(AlertStatus.ACTIVA);
        if (activeAlerts.isEmpty()) {
            return;
        }

        Map<Long, PlayerQuote> quoteMap = event.getQuotes().stream()
                .filter(q -> q.getPlayer() != null)
                .collect(Collectors.toMap(
                        q -> q.getPlayer().getId(),
                        q -> q,
                        (existing, replacement) -> existing
                ));

        for (PriceAlert alert : activeAlerts) {
            Long playerId = alert.getPlayer().getId();
            PlayerQuote newQuote = quoteMap.get(playerId);
            if (newQuote == null) {
                continue;
            }

            boolean conditionMet = evaluateCondition(alert.getCondition(), newQuote.getPrice(), alert.getTargetPrice());
            if (conditionMet) {
                triggerAlert(alert, newQuote);
            }
        }
    }

    public boolean evaluateCondition(AlertCondition condition, BigDecimal currentPrice, BigDecimal targetPrice) {
        if (currentPrice == null || targetPrice == null) {
            return false;
        }
        return switch (condition) {
            case ABOVE -> currentPrice.compareTo(targetPrice) >= 0;
            case BELOW -> currentPrice.compareTo(targetPrice) <= 0;
        };
    }

    private void triggerAlert(PriceAlert alert, PlayerQuote quote) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        alert.setStatus(AlertStatus.DISPARADA);
        alert.setTriggeredAt(now);
        priceAlertRepository.save(alert);

        String playerName = alert.getPlayer().getFirstName() + " " + alert.getPlayer().getLastName();
        PriceAlertEventDto eventDto = new PriceAlertEventDto(
                alert.getId(),
                alert.getPlayer().getId(),
                playerName,
                alert.getCondition(),
                alert.getTargetPrice(),
                quote.getPrice(),
                now
        );

        log.info("Disparando alerta de precio ID {} para usuario {} sobre jugador {}",
                alert.getId(), alert.getUser().getId(), playerName);

        alertStreamService.sendAlert(alert.getUser().getId(), eventDto);
    }
}
