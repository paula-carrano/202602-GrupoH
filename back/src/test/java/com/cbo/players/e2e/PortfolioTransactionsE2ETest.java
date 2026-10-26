package com.cbo.players.e2e;

import com.cbo.players.model.*;
import com.cbo.players.repository.*;
import com.cbo.players.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PortfolioTransactionsE2ETest extends AbstractE2ETest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerQuoteRepository playerQuoteRepository;

    @Autowired
    private PortfolioPositionRepository portfolioPositionRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;
    private String userToken;
    private Player player;

    @BeforeEach
    void setUp() {
        cleanupDatabase();

        user = userRepository.findByUsername("user_portfolio_test")
                .orElseGet(() -> userRepository.save(new User(
                        "user_portfolio_test",
                        "portfolio_test@test.com",
                        passwordEncoder.encode("Pass123!"),
                        UserRole.ROLE_USER
                )));
        user.setCreditsBalance(new BigDecimal("150.0000"));
        user = userRepository.save(user);

        userToken = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), "ROLE_USER");

        player = playerRepository.save(new Player(
                "Rodri",
                "Hernandez",
                LocalDate.of(1996, 6, 22),
                "Spanish",
                PlayerPosition.MIDFIELDER,
                "Manchester City",
                "Premier League"
        ));

        playerQuoteRepository.save(new PlayerQuote(
                player,
                new BigDecimal("20.0000"),
                "CRD",
                LocalDateTime.now(ZoneOffset.UTC),
                "Balanced",
                1
        ));

        // Position: 5 tokens @ 15.0000 average buy price
        portfolioPositionRepository.save(new PortfolioPosition(
                user,
                player,
                5,
                new BigDecimal("15.0000")
        ));

        // Add an audit transaction
        transactionRepository.save(new Transaction(
                user.getId(),
                OperationType.COMPRA,
                player.getId(),
                5,
                new BigDecimal("15.0000"),
                new BigDecimal("75.0000"),
                new BigDecimal("225.0000"),
                new BigDecimal("150.0000"),
                0,
                5,
                1
        ));
    }

    @Test
    @DisplayName("US5 - Consulta de portfolio consolidado con métricas financieras y desglose de posiciones")
    void getPortfolio_success() throws Exception {
        // Position: 5 tokens @ 15.0000 = 75.0 cost basis
        // Current quote = 20.0000 -> 100.0 current value
        // PnL nominal = +25.0, PnL percentage = +33.3333%
        mockMvc.perform(get("/api/users/{id}/portfolio", user.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.creditsBalance").value(150.0))
                .andExpect(jsonPath("$.totalInvestedCost").value(75.0))
                .andExpect(jsonPath("$.totalCurrentValue").value(100.0))
                .andExpect(jsonPath("$.totalPnLNominal").value(25.0))
                .andExpect(jsonPath("$.totalPnLPercentage").value(33.3333))
                .andExpect(jsonPath("$.positions", hasSize(1)))
                .andExpect(jsonPath("$.positions[0].playerId").value(player.getId()))
                .andExpect(jsonPath("$.positions[0].tokensQuantity").value(5))
                .andExpect(jsonPath("$.positions[0].averageBuyPrice").value(15.0))
                .andExpect(jsonPath("$.positions[0].currentQuote").value(20.0))
                .andExpect(jsonPath("$.positions[0].pnlNominal").value(25.0));
    }

    @Test
    @DisplayName("US5 - Consulta de historial auditable de transacciones paginado")
    void getTransactions_success() throws Exception {
        mockMvc.perform(get("/api/users/{id}/transactions", user.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].operationType").value("COMPRA"))
                .andExpect(jsonPath("$.content[0].playerId").value(player.getId()))
                .andExpect(jsonPath("$.content[0].quantity").value(5))
                .andExpect(jsonPath("$.content[0].totalAmount").value(75.0));
    }
}
