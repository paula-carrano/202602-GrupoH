package com.cbo.players.e2e;

import com.cbo.players.dto.request.CreditsTopupRequestDto;
import com.cbo.players.model.OperationType;
import com.cbo.players.model.Transaction;
import com.cbo.players.model.User;
import com.cbo.players.model.UserRole;
import com.cbo.players.repository.TransactionRepository;
import com.cbo.players.repository.UserRepository;
import com.cbo.players.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CreditsTopupE2ETest extends AbstractE2ETest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User userA;
    private User userB;
    private String tokenUserA;
    private String tokenUserB;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAllInBatch();

        userA = userRepository.findByUsername("user_topup_a")
                .orElseGet(() -> userRepository.save(new User(
                        "user_topup_a",
                        "topup_a@test.com",
                        passwordEncoder.encode("Pass123!"),
                        UserRole.ROLE_USER
                )));
        userA.setCreditsBalance(BigDecimal.ZERO);
        userA = userRepository.save(userA);

        userB = userRepository.findByUsername("user_topup_b")
                .orElseGet(() -> userRepository.save(new User(
                        "user_topup_b",
                        "topup_b@test.com",
                        passwordEncoder.encode("Pass123!"),
                        UserRole.ROLE_USER
                )));

        tokenUserA = jwtTokenProvider.generateToken(userA.getUsername(), userA.getId(), "ROLE_USER");
        tokenUserB = jwtTokenProvider.generateToken(userB.getUsername(), userB.getId(), "ROLE_USER");
    }

    @Test
    @DisplayName("US2 - Recarga simulada exitosa acredita fondos y genera registro en Transaction")
    void topupCredits_success() throws Exception {
        CreditsTopupRequestDto request = new CreditsTopupRequestDto(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/users/{id}/credits/topup", userA.getId())
                        .header("Authorization", "Bearer " + tokenUserA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userA.getId()))
                .andExpect(jsonPath("$.operationType").value("RECARGA"))
                .andExpect(jsonPath("$.amountCredited").value(100.0))
                .andExpect(jsonPath("$.balanceBefore").value(0.0))
                .andExpect(jsonPath("$.balanceAfter").value(100.0))
                .andExpect(jsonPath("$.transactionId").isNumber());

        User updatedUser = userRepository.findById(userA.getId()).orElseThrow();
        assertThat(updatedUser.getCreditsBalance()).isEqualByComparingTo("100.0000");

        List<Transaction> transactions = transactionRepository.findByActorUserId(userA.getId());
        assertThat(transactions).hasSize(1);
        Transaction tx = transactions.get(0);
        assertThat(tx.getOperationType()).isEqualTo(OperationType.RECARGA);
        assertThat(tx.getTotalAmount()).isEqualByComparingTo("100.0000");
        assertThat(tx.getBalanceBefore()).isEqualByComparingTo("0.0000");
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("100.0000");
        assertThat(tx.getPlayerId()).isNull();
    }

    @Test
    @DisplayName("US2 - Recarga con monto inválido (negativo o cero) retorna 400 Bad Request")
    void topupCredits_invalidAmount() throws Exception {
        CreditsTopupRequestDto request = new CreditsTopupRequestDto(new BigDecimal("-10.00"));

        mockMvc.perform(post("/api/users/{id}/credits/topup", userA.getId())
                        .header("Authorization", "Bearer " + tokenUserA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("US2 - Intento de recarga a otro usuario sin privilegios retorna 403 Forbidden")
    void topupCredits_forbiddenForOtherUser() throws Exception {
        CreditsTopupRequestDto request = new CreditsTopupRequestDto(new BigDecimal("50.00"));

        mockMvc.perform(post("/api/users/{id}/credits/topup", userA.getId())
                        .header("Authorization", "Bearer " + tokenUserB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
