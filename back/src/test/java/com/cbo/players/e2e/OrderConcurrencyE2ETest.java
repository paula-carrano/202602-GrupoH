package com.cbo.players.e2e;

import com.cbo.players.model.*;
import com.cbo.players.repository.*;
import com.cbo.players.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConcurrencyE2ETest extends AbstractE2ETest {

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
    private ValuationStrategyRepository valuationStrategyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrderService orderService;

    private User superuser;
    private Player player;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAllInBatch();
        portfolioPositionRepository.deleteAllInBatch();
        playerQuoteRepository.deleteAllInBatch();
        playerRepository.deleteAllInBatch();

        superuser = userRepository.findByUsername("super_admin_concurrency")
                .orElseGet(() -> userRepository.save(new User(
                        "super_admin_concurrency",
                        "superuser_concurrency@test.com",
                        passwordEncoder.encode("Pass123!"),
                        UserRole.ROLE_SUPERUSER
                )));

        valuationStrategyRepository.findByActiveTrue().orElseGet(() ->
                valuationStrategyRepository.save(new ValuationStrategy(
                        "BALANCED",
                        "Balanced",
                        "Strategy",
                        1,
                        true,
                        new BigDecimal("1.0000"),
                        1.0, 0.8, 0.01, 0.1, 0.2, 1.5, -0.5
                ))
        );

        player = playerRepository.save(new Player(
                "Kylian",
                "Mbappe",
                LocalDate.of(1998, 12, 20),
                "French",
                PlayerPosition.FORWARD,
                "Real Madrid",
                "La Liga"
        ));

        playerQuoteRepository.save(new PlayerQuote(
                player,
                new BigDecimal("5.0000"),
                "CRD",
                LocalDateTime.now(ZoneOffset.UTC),
                "Balanced",
                1
        ));

        // Initial 100 tokens held by superuser
        portfolioPositionRepository.save(new PortfolioPosition(
                superuser,
                player,
                100,
                new BigDecimal("5.0000")
        ));
    }

    @Test
    @DisplayName("US2 - Invariante estricto de 100 tokens bajo compras concurrentes que saturan la emisión")
    void test100TokenInvariantUnderHighConcurrency() throws InterruptedException {
        int threadCount = 10;
        int tokensPerRequest = 15; // 10 * 15 = 150 requested tokens (exceeds 100)

        List<User> buyers = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            User buyer = userRepository.findByUsername("concurrent_buyer_" + index)
                    .orElseGet(() -> userRepository.save(new User(
                            "concurrent_buyer_" + index,
                            "concurrent_buyer_" + index + "@test.com",
                            passwordEncoder.encode("Pass123!"),
                            UserRole.ROLE_USER
                    )));
            buyer.setCreditsBalance(new BigDecimal("1000.0000")); // Plenty of credits
            buyers.add(userRepository.save(buyer));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successfulOrders = new AtomicInteger(0);
        AtomicInteger rejectedOrders = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final User buyer = buyers.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderService.executeBuyOrder(buyer.getId(), player.getId(), tokensPerRequest);
                    successfulOrders.incrementAndGet();
                } catch (Exception e) {
                    rejectedOrders.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successfulOrders.get() + rejectedOrders.get()).isEqualTo(threadCount);

        // Verification of invariant: total tokens across all positions must equal exactly 100
        Integer totalTokensInDb = portfolioPositionRepository.sumTokensByPlayerId(player.getId());
        assertThat(totalTokensInDb).isEqualTo(100);

        PortfolioPosition superuserPosition = portfolioPositionRepository
                .findByUserIdAndPlayerId(superuser.getId(), player.getId())
                .orElseThrow();

        int tokensBought = successfulOrders.get() * tokensPerRequest;
        assertThat(superuserPosition.getTokensQuantity()).isEqualTo(100 - tokensBought);
    }
}
