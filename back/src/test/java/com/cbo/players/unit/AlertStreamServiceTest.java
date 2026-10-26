package com.cbo.players.unit;

import com.cbo.players.dto.response.PriceAlertEventDto;
import com.cbo.players.model.AlertCondition;
import com.cbo.players.service.AlertStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlertStreamServiceTest {

    private AlertStreamService alertStreamService;

    @BeforeEach
    void setUp() {
        alertStreamService = new AlertStreamService();
    }

    @Test
    @DisplayName("subscribe registra el emitter y aumenta la cuenta de suscriptores activos")
    void subscribe_addsEmitterAndSendsInit() {
        assertThat(alertStreamService.getActiveSubscribersCount()).isZero();

        SseEmitter emitter = alertStreamService.subscribe(10L);

        assertThat(emitter).isNotNull();
        assertThat(alertStreamService.getActiveSubscribersCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Callbacks de onCompletion, onTimeout y onError remueven el emitter correctamente")
    @SuppressWarnings("unchecked")
    void emitterCallbacks_removeEmitter() throws Exception {
        SseEmitter emitter1 = alertStreamService.subscribe(1L);
        SseEmitter emitter2 = alertStreamService.subscribe(2L);
        SseEmitter emitter3 = alertStreamService.subscribe(3L);

        assertThat(alertStreamService.getActiveSubscribersCount()).isEqualTo(3);

        // Disparar timeout callback
        Field timeoutField = ResponseBodyEmitter.class.getDeclaredField("timeoutCallback");
        timeoutField.setAccessible(true);
        Runnable timeoutCallback = (Runnable) timeoutField.get(emitter1);
        assertThat(timeoutCallback).isNotNull();
        timeoutCallback.run();
        assertThat(alertStreamService.getActiveSubscribersCount()).isEqualTo(2);

        // Disparar error callback
        Field errorField = ResponseBodyEmitter.class.getDeclaredField("errorCallback");
        errorField.setAccessible(true);
        Consumer<Throwable> errorCallback = (Consumer<Throwable>) errorField.get(emitter2);
        assertThat(errorCallback).isNotNull();
        errorCallback.accept(new IOException("Simulated network timeout"));
        assertThat(alertStreamService.getActiveSubscribersCount()).isEqualTo(1);

        // Disparar completion callback
        Field completionField = ResponseBodyEmitter.class.getDeclaredField("completionCallback");
        completionField.setAccessible(true);
        Runnable completionCallback = (Runnable) completionField.get(emitter3);
        assertThat(completionCallback).isNotNull();
        completionCallback.run();
        assertThat(alertStreamService.getActiveSubscribersCount()).isZero();
    }

    @Test
    @DisplayName("sendAlert no realiza acciones si el usuario no tiene emitters suscritos")
    void sendAlert_withNoEmittersDoesNothing() {
        PriceAlertEventDto event = new PriceAlertEventDto(
                1L, 5L, "Kylian Mbappe",
                AlertCondition.ABOVE, new BigDecimal("35.0000"), new BigDecimal("36.0000"),
                LocalDateTime.now()
        );

        // No debe lanzar excepción
        alertStreamService.sendAlert(99L, event);
        assertThat(alertStreamService.getActiveSubscribersCount()).isZero();
    }

    @Test
    @DisplayName("sendAlert envía el evento a los emitters del usuario y remueve emitters con fallos")
    @SuppressWarnings("unchecked")
    void sendAlert_sendsEventAndRemovesFailedEmitters() throws Exception {
        PriceAlertEventDto event = new PriceAlertEventDto(
                1L, 5L, "Kylian Mbappe",
                AlertCondition.ABOVE, new BigDecimal("35.0000"), new BigDecimal("36.0000"),
                LocalDateTime.now()
        );

        SseEmitter mockSuccess = mock(SseEmitter.class);
        SseEmitter mockFailure = mock(SseEmitter.class);
        doThrow(new IOException("Connection lost")).when(mockFailure).send(any(SseEmitter.SseEventBuilder.class));

        Field mapField = AlertStreamService.class.getDeclaredField("userEmitters");
        mapField.setAccessible(true);
        Map<Long, List<SseEmitter>> userEmitters = (Map<Long, List<SseEmitter>>) mapField.get(alertStreamService);

        List<SseEmitter> list = new CopyOnWriteArrayList<>(List.of(mockSuccess, mockFailure));
        userEmitters.put(10L, list);

        assertThat(alertStreamService.getActiveSubscribersCount()).isEqualTo(2);

        alertStreamService.sendAlert(10L, event);

        verify(mockSuccess).send(any(SseEmitter.SseEventBuilder.class));
        verify(mockFailure).send(any(SseEmitter.SseEventBuilder.class));
        // El emitter con fallo debe haber sido removido
        assertThat(alertStreamService.getActiveSubscribersCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("sendHeartbeat envía ping a todos los suscriptores y remueve los inactivos")
    @SuppressWarnings("unchecked")
    void sendHeartbeat_pingsAllAndCleansUpFailures() throws Exception {
        // Inicialmente vacío: retorna inmediatamente sin error
        alertStreamService.sendHeartbeat();

        SseEmitter mockSuccess = mock(SseEmitter.class);
        SseEmitter mockFailure = mock(SseEmitter.class);
        doThrow(new RuntimeException("Closed pipe")).when(mockFailure).send(any(SseEmitter.SseEventBuilder.class));

        Field mapField = AlertStreamService.class.getDeclaredField("userEmitters");
        mapField.setAccessible(true);
        Map<Long, List<SseEmitter>> userEmitters = (Map<Long, List<SseEmitter>>) mapField.get(alertStreamService);

        userEmitters.put(1L, new CopyOnWriteArrayList<>(List.of(mockSuccess)));
        userEmitters.put(2L, new CopyOnWriteArrayList<>(List.of(mockFailure)));

        assertThat(alertStreamService.getActiveSubscribersCount()).isEqualTo(2);

        alertStreamService.sendHeartbeat();

        verify(mockSuccess).send(any(SseEmitter.SseEventBuilder.class));
        verify(mockFailure).send(any(SseEmitter.SseEventBuilder.class));

        // Usuario 2 quedó sin emitters, por lo que su entrada se remueve
        assertThat(alertStreamService.getActiveSubscribersCount()).isEqualTo(1);
        assertThat(userEmitters.containsKey(2L)).isFalse();
    }
}
