package com.cbo.players.service;

import com.cbo.players.dto.response.PriceAlertEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AlertStreamService {

    private static final Logger log = LoggerFactory.getLogger(AlertStreamService.class);
    private static final Long EMITTER_TIMEOUT = 1800000L; // 30 minutes

    private final Map<Long, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);

        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> {
            log.debug("SSE timeout para usuario {}", userId);
            removeEmitter(userId, emitter);
        });
        emitter.onError(e -> {
            log.debug("SSE error para usuario {}: {}", userId, e.getMessage());
            removeEmitter(userId, emitter);
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(Map.of("message", "Conexión SSE establecida para alertas de precio", "timestamp", Instant.now().toString())));
        } catch (IOException e) {
            log.warn("Error enviando evento init SSE para usuario {}: {}", userId, e.getMessage());
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    public void sendAlert(Long userId, PriceAlertEventDto alertEvent) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("price-alert")
                        .data(alertEvent));
            } catch (Exception e) {
                log.warn("Fallo al enviar alerta SSE al usuario {}: {}", userId, e.getMessage());
                removeEmitter(userId, emitter);
            }
        }
    }

    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        if (userEmitters.isEmpty()) {
            return;
        }

        Map<String, String> pingData = Map.of("timestamp", Instant.now().toString());

        userEmitters.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("ping")
                            .data(pingData));
                } catch (Exception e) {
                    removeEmitter(userId, emitter);
                }
            }
        });
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
    }

    public int getActiveSubscribersCount() {
        return userEmitters.values().stream().mapToInt(List::size).sum();
    }
}
