package com.cbo.players.unit;

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
import com.cbo.players.service.AlertService;
import com.cbo.players.service.AlertStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private PriceAlertRepository priceAlertRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private AlertStreamService alertStreamService;

    @InjectMocks
    private AlertService alertService;

    private User user;
    private Player player;

    @BeforeEach
    void setUp() {
        user = new User("alert_user", "alert@test.com", "hash", UserRole.ROLE_USER);
        user.setId(12L);

        player = new Player("Erling", "Haaland", LocalDate.of(2000, 7, 21),
                "Norwegian", PlayerPosition.FORWARD, "Manchester City", "Premier League");
        player.setId(45L);
        player.setActive(true);
    }

    @Test
    @DisplayName("US4 - Evaluación de condición ABOVE y BELOW")
    void evaluateCondition() {
        assertThat(alertService.evaluateCondition(AlertCondition.ABOVE, new BigDecimal("25.0000"), new BigDecimal("20.0000"))).isTrue();
        assertThat(alertService.evaluateCondition(AlertCondition.ABOVE, new BigDecimal("20.0000"), new BigDecimal("20.0000"))).isTrue();
        assertThat(alertService.evaluateCondition(AlertCondition.ABOVE, new BigDecimal("19.9999"), new BigDecimal("20.0000"))).isFalse();

        assertThat(alertService.evaluateCondition(AlertCondition.BELOW, new BigDecimal("15.0000"), new BigDecimal("20.0000"))).isTrue();
        assertThat(alertService.evaluateCondition(AlertCondition.BELOW, new BigDecimal("20.0000"), new BigDecimal("20.0000"))).isTrue();
        assertThat(alertService.evaluateCondition(AlertCondition.BELOW, new BigDecimal("20.0001"), new BigDecimal("20.0000"))).isFalse();
    }

    @Test
    @DisplayName("US4 - Creación exitosa de alerta de precio en estado ACTIVA")
    void createAlert_success() {
        CreateAlertRequestDto request = new CreateAlertRequestDto(45L, AlertCondition.ABOVE, new BigDecimal("25.0000"));

        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(playerRepository.findById(45L)).thenReturn(Optional.of(player));
        when(priceAlertRepository.save(any(PriceAlert.class))).thenAnswer(i -> {
            PriceAlert a = i.getArgument(0);
            a.setId(14L);
            return a;
        });

        PriceAlertResponseDto response = alertService.createAlert(12L, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(14L);
        assertThat(response.userId()).isEqualTo(12L);
        assertThat(response.playerId()).isEqualTo(45L);
        assertThat(response.condition()).isEqualTo(AlertCondition.ABOVE);
        assertThat(response.targetPrice()).isEqualByComparingTo("25.0000");
        assertThat(response.status()).isEqualTo(AlertStatus.ACTIVA);
    }

    @Test
    @DisplayName("US4 - Creación rechazada con precio objetivo menor o igual a cero o nulo")
    void createAlert_invalidAmount() {
        CreateAlertRequestDto requestZero = new CreateAlertRequestDto(45L, AlertCondition.ABOVE, BigDecimal.ZERO);
        CreateAlertRequestDto requestNull = new CreateAlertRequestDto(45L, AlertCondition.ABOVE, null);

        assertThatThrownBy(() -> alertService.createAlert(12L, requestZero))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("mayor a cero")
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT);
                });

        assertThatThrownBy(() -> alertService.createAlert(12L, requestNull))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("mayor a cero");
    }

    @Test
    @DisplayName("US4 - QuotesRecalculatedEvent dispara alertas activas y notifica vía SSE")
    void onQuotesRecalculated_triggersAlert() {
        PriceAlert alert = new PriceAlert(user, player, AlertCondition.ABOVE, new BigDecimal("25.0000"));
        alert.setId(14L);

        when(priceAlertRepository.findByStatus(AlertStatus.ACTIVA)).thenReturn(List.of(alert));

        PlayerQuote quote = new PlayerQuote(player, new BigDecimal("27.4000"), "CRD",
                LocalDateTime.now(ZoneOffset.UTC), "Balanced", 1);

        QuotesRecalculatedEvent event = new QuotesRecalculatedEvent(this, List.of(quote), null, LocalDateTime.now(ZoneOffset.UTC));

        alertService.onQuotesRecalculated(event);

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.DISPARADA);
        assertThat(alert.getTriggeredAt()).isNotNull();

        verify(priceAlertRepository).save(alert);

        ArgumentCaptor<PriceAlertEventDto> eventCaptor = ArgumentCaptor.forClass(PriceAlertEventDto.class);
        verify(alertStreamService).sendAlert(eq(12L), eventCaptor.capture());

        PriceAlertEventDto payload = eventCaptor.getValue();
        assertThat(payload.alertId()).isEqualTo(14L);
        assertThat(payload.playerId()).isEqualTo(45L);
        assertThat(payload.currentPrice()).isEqualByComparingTo("27.4000");
    }

    @Test
    @DisplayName("US4 - Eliminación de alerta de otro usuario rechazada con 403 ACCESS_DENIED")
    void deleteAlert_accessDenied() {
        PriceAlert alert = new PriceAlert(user, player, AlertCondition.ABOVE, new BigDecimal("25.0000"));
        alert.setId(14L);

        when(priceAlertRepository.findById(14L)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> alertService.deleteAlert(14L, 999L, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });

        verify(priceAlertRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteAlert elimina la alerta cuando el usuario es dueño o posee rol privilegiado")
    void deleteAlert_successWhenOwnerOrPrivileged() {
        PriceAlert alert = new PriceAlert(user, player, AlertCondition.ABOVE, new BigDecimal("25.0000"));
        alert.setId(14L);

        when(priceAlertRepository.findById(14L)).thenReturn(Optional.of(alert));

        // Dueño elimina su propia alerta
        alertService.deleteAlert(14L, 12L, false);
        verify(priceAlertRepository).delete(alert);

        // Usuario privilegiado elimina alerta de otro usuario
        alertService.deleteAlert(14L, 999L, true);
        verify(priceAlertRepository, times(2)).delete(alert);
    }

    @Test
    @DisplayName("deleteAlert lanza ApiException 404 si la alerta no existe")
    void deleteAlert_throwsNotFound() {
        when(priceAlertRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.deleteAlert(99L, 12L, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.ALERT_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("getUserAlerts retorna listado mapeado a PriceAlertResponseDto")
    void getUserAlerts_returnsMappedList() {
        PriceAlert alert = new PriceAlert(user, player, AlertCondition.ABOVE, new BigDecimal("25.0000"));
        alert.setId(14L);
        when(priceAlertRepository.findByUserIdOrderByCreatedAtDesc(12L)).thenReturn(List.of(alert));

        List<PriceAlertResponseDto> result = alertService.getUserAlerts(12L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(14L);
    }

    @Test
    @DisplayName("onQuotesRecalculated no ejecuta acciones con eventos sin cotizaciones o sin alertas activas")
    void onQuotesRecalculated_handlesEmptyScenarios() {
        // Evento con cotizaciones nulas o vacías
        QuotesRecalculatedEvent eventNull = mock(QuotesRecalculatedEvent.class);
        when(eventNull.getQuotes()).thenReturn(null);
        alertService.onQuotesRecalculated(eventNull);

        QuotesRecalculatedEvent eventEmpty = mock(QuotesRecalculatedEvent.class);
        when(eventEmpty.getQuotes()).thenReturn(List.of());
        alertService.onQuotesRecalculated(eventEmpty);

        verify(priceAlertRepository, never()).findByStatus(any());

        // Evento con cotizaciones pero sin alertas activas en BD
        PlayerQuote quote = new PlayerQuote(player, new BigDecimal("30.0000"), "CRD", LocalDateTime.now(), "B", 1);
        QuotesRecalculatedEvent validEvent = new QuotesRecalculatedEvent(this, List.of(quote), null, LocalDateTime.now());
        when(priceAlertRepository.findByStatus(AlertStatus.ACTIVA)).thenReturn(List.of());

        alertService.onQuotesRecalculated(validEvent);
        verify(alertStreamService, never()).sendAlert(any(), any());
    }

    @Test
    @DisplayName("onQuotesRecalculated omite cotizaciones con jugador nulo y jugadores sin cotización nueva")
    void onQuotesRecalculated_handlesNullPlayersAndConditionNotMet() {
        PriceAlert alert = new PriceAlert(user, player, AlertCondition.ABOVE, new BigDecimal("50.0000"));
        alert.setId(14L);
        when(priceAlertRepository.findByStatus(AlertStatus.ACTIVA)).thenReturn(List.of(alert));

        // Cotización con player nulo y cotización que no cumple la condición (30 < 50)
        PlayerQuote quoteNullPlayer = mock(PlayerQuote.class);
        when(quoteNullPlayer.getPlayer()).thenReturn(null);

        PlayerQuote quoteLowPrice = new PlayerQuote(player, new BigDecimal("30.0000"), "CRD", LocalDateTime.now(), "B", 1);
        PlayerQuote duplicateQuote = new PlayerQuote(player, new BigDecimal("30.0000"), "CRD", LocalDateTime.now(), "B", 1);

        QuotesRecalculatedEvent event = new QuotesRecalculatedEvent(this, List.of(quoteNullPlayer, quoteLowPrice, duplicateQuote), null, LocalDateTime.now());

        alertService.onQuotesRecalculated(event);

        // La condición no se cumplió, por lo que la alerta no se dispara
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.ACTIVA);
        verify(alertStreamService, never()).sendAlert(any(), any());
    }

    @Test
    @DisplayName("evaluateCondition retorna false cuando los precios comparados son nulos")
    void evaluateCondition_returnsFalseOnNullPrices() {
        assertThat(alertService.evaluateCondition(AlertCondition.ABOVE, null, new BigDecimal("10.0000"))).isFalse();
        assertThat(alertService.evaluateCondition(AlertCondition.ABOVE, new BigDecimal("10.0000"), null)).isFalse();
        assertThat(alertService.evaluateCondition(AlertCondition.BELOW, null, null)).isFalse();
    }
}
