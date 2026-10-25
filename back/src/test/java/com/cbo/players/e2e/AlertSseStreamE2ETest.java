package com.cbo.players.e2e;

import com.cbo.players.dto.request.CreateAlertRequestDto;
import com.cbo.players.event.QuotesRecalculatedEvent;
import com.cbo.players.model.*;
import com.cbo.players.repository.*;
import com.cbo.players.security.JwtTokenProvider;
import com.cbo.players.service.AlertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AlertSseStreamE2ETest extends AbstractE2ETest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PriceAlertRepository priceAlertRepository;

    @Autowired
    private PlayerQuoteRepository playerQuoteRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertService alertService;

    private User user;
    private String userToken;
    private Player player;

    @BeforeEach
    void setUp() {
        priceAlertRepository.deleteAllInBatch();
        playerQuoteRepository.deleteAllInBatch();
        playerRepository.deleteAllInBatch();

        user = userRepository.findByUsername("user_sse_test")
                .orElseGet(() -> userRepository.save(new User(
                        "user_sse_test",
                        "sse_test@test.com",
                        passwordEncoder.encode("Pass123!"),
                        UserRole.ROLE_USER
                )));

        userToken = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), "ROLE_USER");

        player = playerRepository.save(new Player(
                "Vinicius",
                "Junior",
                LocalDate.of(2000, 7, 12),
                "Brazilian",
                PlayerPosition.FORWARD,
                "Real Madrid",
                "La Liga"
        ));
    }

    @Test
    @DisplayName("US4 - Creación, listado y eliminación de alerta de precio vía REST")
    void alertCrud_success() throws Exception {
        CreateAlertRequestDto createRequest = new CreateAlertRequestDto(player.getId(), AlertCondition.ABOVE, new BigDecimal("30.0000"));

        // 1. Create alert
        String responseContent = mockMvc.perform(post("/api/alerts")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId").value(player.getId()))
                .andExpect(jsonPath("$.condition").value("ABOVE"))
                .andExpect(jsonPath("$.targetPrice").value(30.0))
                .andExpect(jsonPath("$.status").value("ACTIVA"))
                .andReturn().getResponse().getContentAsString();

        Long alertId = objectMapper.readTree(responseContent).get("id").asLong();

        // 2. List alerts
        mockMvc.perform(get("/api/alerts")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(alertId));

        // 3. Delete alert
        mockMvc.perform(delete("/api/alerts/{id}", alertId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        assertThat(priceAlertRepository.findById(alertId)).isEmpty();
    }

    @Test
    @DisplayName("US4 - Endpoint SSE /api/alerts/stream responde text/event-stream")
    void sseStreamConnection_success() throws Exception {
        mockMvc.perform(get("/api/alerts/stream")
                        .header("Authorization", "Bearer " + userToken)
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    @Test
    @DisplayName("US4 - Disparo reactivo de alerta tras recálculo de cotizaciones")
    void quoteRecalculation_triggersPriceAlert() {
        PriceAlert alert = priceAlertRepository.save(new PriceAlert(
                user,
                player,
                AlertCondition.ABOVE,
                new BigDecimal("20.0000")
        ));

        PlayerQuote newQuote = playerQuoteRepository.save(new PlayerQuote(
                player,
                new BigDecimal("25.0000"),
                "CRD",
                LocalDateTime.now(ZoneOffset.UTC),
                "Balanced",
                1
        ));

        // Dispatch event
        QuotesRecalculatedEvent event = new QuotesRecalculatedEvent(
                this,
                List.of(newQuote),
                null,
                LocalDateTime.now(ZoneOffset.UTC)
        );

        alertService.onQuotesRecalculated(event);

        PriceAlert updatedAlert = priceAlertRepository.findById(alert.getId()).orElseThrow();
        assertThat(updatedAlert.getStatus()).isEqualTo(AlertStatus.DISPARADA);
        assertThat(updatedAlert.getTriggeredAt()).isNotNull();
    }
}
