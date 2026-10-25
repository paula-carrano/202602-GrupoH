package com.cbo.players.e2e;

import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.model.PlayerQuote;
import com.cbo.players.model.User;
import com.cbo.players.model.UserRole;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.UserRepository;
import com.cbo.players.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlayerQuotesPaginationE2ETest extends AbstractE2ETest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerQuoteRepository playerQuoteRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String userToken;
    private Player player;

    @BeforeEach
    void setUp() {
        playerQuoteRepository.deleteAllInBatch();
        playerRepository.deleteAllInBatch();

        testUser = userRepository.findByUsername("user_quotes_test")
                .orElseGet(() -> userRepository.save(new User(
                        "user_quotes_test",
                        "quotes_test@test.com",
                        passwordEncoder.encode("Pass123!"),
                        UserRole.ROLE_USER
                )));

        userToken = jwtTokenProvider.generateToken(testUser.getUsername(), testUser.getId(), "ROLE_USER");

        player = playerRepository.save(new Player(
                "Luka",
                "Modric",
                LocalDate.of(1985, 9, 9),
                "Croatian",
                PlayerPosition.MIDFIELDER,
                "Real Madrid",
                "La Liga"
        ));
    }

    @Test
    @DisplayName("US3 - Consulta paginada devuelve serie histórica de cotizaciones con metadata")
    void getPaginatedQuotes_success() throws Exception {
        LocalDateTime baseTime = LocalDateTime.of(2026, 10, 1, 10, 0);
        for (int i = 0; i < 25; i++) {
            playerQuoteRepository.save(new PlayerQuote(
                    player,
                    new BigDecimal(10 + i + ".0000"),
                    "CRD",
                    baseTime.plusDays(i),
                    "Balanced",
                    1
            ));
        }

        mockMvc.perform(get("/api/players/{id}/quotes", player.getId())
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.content[0].price").value(34.0)); // Most recent quote
    }

    @Test
    @DisplayName("US3 - Consulta por fecha devuelve un único objeto con la cotización vigente a esa fecha sin paginación")
    void getQuoteBySpecificDate_success() throws Exception {
        playerQuoteRepository.save(new PlayerQuote(
                player,
                new BigDecimal("2.5000"),
                "CRD",
                LocalDateTime.of(2026, 10, 1, 12, 0),
                "Rendimiento Ofensivo",
                1
        ));

        playerQuoteRepository.save(new PlayerQuote(
                player,
                new BigDecimal("3.2000"),
                "CRD",
                LocalDateTime.of(2026, 10, 8, 12, 0),
                "Rendimiento Global Equilibrado",
                1
        ));

        mockMvc.perform(get("/api/players/{id}/quotes", player.getId())
                        .param("date", "2026-10-05")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(player.getId()))
                .andExpect(jsonPath("$.effectiveDate").value("2026-10-05"))
                .andExpect(jsonPath("$.price").value(2.5))
                .andExpect(jsonPath("$.strategyName").value("Rendimiento Ofensivo"))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    @DisplayName("US3 - Consulta a fecha previa a cualquier cotización retorna valor nominal base 1.0")
    void getQuoteByDate_priorToFirstRecord() throws Exception {
        playerQuoteRepository.save(new PlayerQuote(
                player,
                new BigDecimal("5.0000"),
                "CRD",
                LocalDateTime.of(2026, 10, 15, 12, 0),
                "Rendimiento Ofensivo",
                1
        ));

        mockMvc.perform(get("/api/players/{id}/quotes", player.getId())
                        .param("date", "2026-01-01")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(player.getId()))
                .andExpect(jsonPath("$.price").value(1.0))
                .andExpect(jsonPath("$.effectiveDate").value("2026-01-01"));
    }
}
