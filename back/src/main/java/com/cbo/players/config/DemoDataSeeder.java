package com.cbo.players.config;

import com.cbo.players.adapter.dto.PlayerStatsDto;
import com.cbo.players.model.*;
import com.cbo.players.model.records.TransactionData;
import com.cbo.players.model.records.ValuationWeights;
import com.cbo.players.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Component
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true", matchIfMissing = false)
public class DemoDataSeeder implements ApplicationRunner {

        private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

        // Emails de usuarios demo
        private static final String DEMO_INVERSOR_PASSWORD = System.getenv("DEMO_INVERSOR_PASSWORD");
        private static final String DEMO_ADMIN_PASSWORD = System.getenv("DEMO_ADMIN_PASSWORD");
        private static final String DEMO_NUEVO_PASSWORD = System.getenv("DEMO_NUEVO_PASSWORD");

        // Constantes de usuarios
        private static final String USER_INVERSOR = "inversor";
        private static final String USER_ADMIN = "admin";
        private static final String USER_NUEVO = "nuevo";

        // Constantes de jugadores
        private static final String PLAYER_HAALAND = "Haaland";
        private static final String PLAYER_MBAPPE = "Mbappé";
        private static final String PLAYER_JUNIOR = "Júnior";
        private static final String PLAYER_DE_BRUYNE = "De Bruyne";
        private static final String PLAYER_BELLINGHAM = "Bellingham";
        private static final String PLAYER_HERNANDEZ = "Hernández";
        private static final String PLAYER_VAN_DIJK = "van Dijk";
        private static final String PLAYER_DAVIES = "Davies";
        private static final String PLAYER_MARTINEZ = "Martínez";

        // Constantes de equipos y ligas
        private static final String TEAM_MANCHESTER_CITY = "Manchester City";
        private static final String TEAM_REAL_MADRID = "Real Madrid";
        private static final String LEAGUE_PREMIER = "Premier League";
        private static final String LEAGUE_LA_LIGA = "La Liga";

        // Constantes de valores monetarios
        private static final String DEFAULT_PRICE = "1.0000";
        private static final String INITIAL_BALANCE = "25000.0000";

        private final UserRepository userRepository;
        private final PlayerRepository playerRepository;
        private final PlayerStatisticsRepository playerStatisticsRepository;
        private final PlayerQuoteRepository playerQuoteRepository;
        private final PortfolioPositionRepository portfolioPositionRepository;
        private final TransactionRepository transactionRepository;
        private final PriceAlertRepository priceAlertRepository;
        private final ValuationStrategyRepository valuationStrategyRepository;
        private final ApiKeyRepository apiKeyRepository;
        private final PasswordEncoder passwordEncoder;

        public DemoDataSeeder(
                        UserRepository userRepository,
                        PlayerRepository playerRepository,
                        PlayerStatisticsRepository playerStatisticsRepository,
                        PlayerQuoteRepository playerQuoteRepository,
                        PortfolioPositionRepository portfolioPositionRepository,
                        TransactionRepository transactionRepository,
                        PriceAlertRepository priceAlertRepository,
                        ValuationStrategyRepository valuationStrategyRepository,
                        ApiKeyRepository apiKeyRepository,
                        PasswordEncoder passwordEncoder) {
                this.userRepository = userRepository;
                this.playerRepository = playerRepository;
                this.playerStatisticsRepository = playerStatisticsRepository;
                this.playerQuoteRepository = playerQuoteRepository;
                this.portfolioPositionRepository = portfolioPositionRepository;
                this.transactionRepository = transactionRepository;
                this.priceAlertRepository = priceAlertRepository;
                this.valuationStrategyRepository = valuationStrategyRepository;
                this.apiKeyRepository = apiKeyRepository;
                this.passwordEncoder = passwordEncoder;
        }

        @Override
        @Transactional
        public void run(ApplicationArguments args) {
                if (userRepository.existsByEmail(DEMO_INVERSOR_EMAIL)) {
                        log.info("Datos de demostración ya inicializados previamente. Omitiendo seeding.");
                        return;
                }

                log.info("Iniciando carga de datos de demostración para Players Market...");
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

                ValuationStrategy activeStrategy = ensureValuationStrategies();
                List<Player> players = seedPlayers();
                seedQuotes(players, activeStrategy, now);

                Map<String, User> users = seedUsers();
                User inversor = users.get(USER_INVERSOR);
                User admin = users.get(USER_ADMIN);

                seedInversorPortfolioAndTransactions(inversor, players, activeStrategy, now);
                seedAdminLiquidity(admin, players);
                seedAlerts(inversor, players, now);
                seedAdminApiKey(admin);

                log.info("Carga de datos de demostración completada exitosamente.");
        }

        private ValuationStrategy ensureValuationStrategies() {
                Optional<ValuationStrategy> activeOpt = valuationStrategyRepository.findByActiveTrue();
                if (activeOpt.isPresent()) {
                        return activeOpt.get();
                }

                ValuationStrategy offensive = new ValuationStrategy(
                                "OFFENSIVE",
                                "Rendimiento Ofensivo",
                                "Ponderación orientada al rendimiento ofensivo y definición",
                                1,
                                true,
                                new BigDecimal(DEFAULT_PRICE),
                                new ValuationWeights(1.5, 1.0, 0.005, 0.2, 0.3, 1.2, -0.3));

                ValuationStrategy balanced = new ValuationStrategy(
                                "BALANCED",
                                "Rendimiento Global Equilibrado",
                                "Ponderación integral de estadísticas ofensivas, minutos y disciplina",
                                1,
                                false,
                                new BigDecimal(DEFAULT_PRICE),
                                new ValuationWeights(1.0, 0.8, 0.01, 0.1, 0.2, 1.5, -0.5));

                valuationStrategyRepository.saveAll(List.of(offensive, balanced));
                return offensive;
        }

        private List<Player> seedPlayers() {
                if (playerRepository.count() > 0) {
                        return playerRepository.findAllByActiveTrue();
                }

                List<PlayerSeedData> seedData = List.of(
                                new PlayerSeedData("Erling", PLAYER_HAALAND, LocalDate.of(2000, 7, 21), "Norway",
                                                PlayerPosition.FORWARD, TEAM_MANCHESTER_CITY, LEAGUE_PREMIER,
                                                new PlayerStatsDto(34, 6, 110, 28, 22, 15, 8.3, 2600, 3, 0)),

                                new PlayerSeedData("Kylian", PLAYER_MBAPPE, LocalDate.of(1998, 12, 20), "France",
                                                PlayerPosition.FORWARD, TEAM_REAL_MADRID, LEAGUE_LA_LIGA,
                                                new PlayerStatsDto(29, 10, 105, 45, 70, 10, 8.4, 2550, 4, 0)),

                                new PlayerSeedData("Vinícius", PLAYER_JUNIOR, LocalDate.of(2000, 7, 12), "Brazil",
                                                PlayerPosition.FORWARD, TEAM_REAL_MADRID, LEAGUE_LA_LIGA,
                                                new PlayerStatsDto(24, 14, 88, 60, 85, 20, 8.2, 2500, 6, 0)),

                                new PlayerSeedData("Kevin", PLAYER_DE_BRUYNE, LocalDate.of(1991, 6, 28), "Belgium",
                                                PlayerPosition.MIDFIELDER, TEAM_MANCHESTER_CITY, LEAGUE_PREMIER,
                                                new PlayerStatsDto(10, 22, 65, 95, 35, 30, 8.1, 2100, 2, 0)),

                                new PlayerSeedData("Jude", PLAYER_BELLINGHAM, LocalDate.of(2003, 6, 29), "England",
                                                PlayerPosition.MIDFIELDER, TEAM_REAL_MADRID, LEAGUE_LA_LIGA,
                                                new PlayerStatsDto(21, 12, 75, 62, 52, 48, 8.3, 2700, 5, 0)),

                                new PlayerSeedData("Rodrigo", PLAYER_HERNANDEZ, LocalDate.of(1996, 6, 22), "Spain",
                                                PlayerPosition.MIDFIELDER, TEAM_MANCHESTER_CITY, LEAGUE_PREMIER,
                                                new PlayerStatsDto(8, 9, 45, 58, 30, 82, 8.0, 2900, 4, 0)),

                                new PlayerSeedData("Virgil", PLAYER_VAN_DIJK, LocalDate.of(1991, 7, 8), "Netherlands",
                                                PlayerPosition.DEFENDER, "Liverpool", LEAGUE_PREMIER,
                                                new PlayerStatsDto(4, 2, 22, 15, 8, 65, 7.8, 2850, 3, 0)),

                                new PlayerSeedData("Alphonso", PLAYER_DAVIES, LocalDate.of(2000, 11, 2), "Canada",
                                                PlayerPosition.DEFENDER, "Bayern Munich", "Bundesliga",
                                                new PlayerStatsDto(3, 8, 30, 40, 68, 55, 7.6, 2300, 3, 0)),

                                new PlayerSeedData("Emiliano", PLAYER_MARTINEZ, LocalDate.of(1992, 9, 2), "Argentina",
                                                PlayerPosition.GOALKEEPER, "Aston Villa", LEAGUE_PREMIER,
                                                new PlayerStatsDto(0, 1, 0, 8, 2, 18, 7.5, 2970, 5, 0)));

                List<Player> createdPlayers = new ArrayList<>();
                long externalId = 1001L;

                for (PlayerSeedData data : seedData) {
                        Player player = new Player(
                                        data.firstName(),
                                        data.lastName(),
                                        data.birthDate(),
                                        data.nationality(),
                                        data.position(),
                                        data.currentTeam(),
                                        data.league());
                        player.setActive(true);
                        player.setWhoscoredId(externalId);
                        player.setFootballDataId(externalId + 5000L);
                        player = playerRepository.saveAndFlush(player);

                        PlayerStatistics stats = new PlayerStatistics(player, externalId, data.stats());
                        playerStatisticsRepository.save(stats);

                        createdPlayers.add(player);
                        externalId++;
                }

                return createdPlayers;
        }

        private void seedQuotes(List<Player> players, ValuationStrategy strategy, LocalDateTime now) {
                if (players.isEmpty()) {
                        return;
                }

                // Curvas históricas calibradas (16 puntos en los últimos 75 días, espaciados
                // cada 5 días)
                // El último elemento corresponde a hoy (now) y coincide con el cálculo de la
                // estrategia activa.
                Map<String, double[]> historicalPriceCurves = Map.of(
                                PLAYER_HAALAND,
                                new double[] { 75.0, 77.0, 79.5, 81.0, 80.0, 82.5, 83.0, 84.5, 84.0, 85.0, 90.0, 94.5,
                                                99.0, 102.0, 106.5, 111.6600 },
                                PLAYER_MBAPPE,
                                new double[] { 70.0, 72.0, 74.5, 76.0, 78.5, 80.0, 83.5, 86.0, 88.5, 91.0, 93.5, 97.0,
                                                100.5, 102.0, 104.5, 106.6300 },
                                PLAYER_JUNIOR,
                                new double[] { 72.0, 74.0, 76.5, 78.0, 81.0, 84.5, 87.0, 90.5, 93.0, 96.5, 100.0, 103.5,
                                                107.0, 110.0, 112.5, 114.6400 },
                                PLAYER_DE_BRUYNE,
                                new double[] { 68.0, 70.0, 72.5, 74.0, 76.0, 78.5, 81.0, 83.5, 86.0, 89.0, 91.5, 94.0,
                                                96.5, 98.0, 100.0, 101.6200 },
                                PLAYER_BELLINGHAM,
                                new double[] { 68.0, 70.0, 72.0, 71.5, 73.0, 75.0, 74.0, 76.5, 78.0, 77.5, 79.0, 80.0,
                                                86.0, 92.5, 98.0, 104.2600 },
                                PLAYER_HERNANDEZ,
                                new double[] { 50.0, 52.0, 53.5, 55.0, 56.5, 58.0, 60.0, 61.5, 63.0, 65.0, 66.5, 68.0,
                                                69.5, 71.0, 72.0, 73.3000 },
                                PLAYER_VAN_DIJK,
                                new double[] { 28.0, 29.0, 30.0, 31.0, 31.5, 32.5, 33.5, 34.0, 35.0, 36.0, 37.0, 37.5,
                                                38.5, 39.0, 39.8, 40.4100 },
                                PLAYER_DAVIES,
                                new double[] { 38.0, 39.5, 41.0, 42.5, 43.5, 45.0, 46.5, 48.0, 49.5, 51.0, 52.5, 53.5,
                                                54.5, 55.5, 56.2, 57.0200 },
                                PLAYER_MARTINEZ, new double[] { 20.0, 20.5, 21.0, 21.5, 22.0, 22.5, 23.0, 23.5, 23.8,
                                                24.0, 24.2, 24.5, 24.7, 24.8, 25.0, 25.9500 });

                List<PlayerQuote> quotesToSave = new ArrayList<>();

                for (Player player : players) {
                        double[] curve = historicalPriceCurves.getOrDefault(player.getLastName(), new double[] {
                                        50.0, 52.0, 54.0, 56.0, 58.0, 60.0, 62.0, 64.0, 66.0, 68.0, 70.0, 72.0, 74.0,
                                        76.0, 78.0, 80.0
                        });

                        int totalPoints = curve.length;
                        for (int i = 0; i < totalPoints; i++) {
                                int daysAgo = (totalPoints - 1 - i) * 5;
                                LocalDateTime quoteTime = now.minusDays(daysAgo);
                                BigDecimal price = BigDecimal.valueOf(curve[i]).setScale(4, RoundingMode.HALF_UP);

                                quotesToSave.add(new PlayerQuote(
                                                player,
                                                price,
                                                "CRD",
                                                quoteTime,
                                                strategy.getName(),
                                                strategy.getVersion()));
                        }
                }

                playerQuoteRepository.saveAll(quotesToSave);
        }

        private Map<String, User> seedUsers() {
                User inversor = new User(
                                USER_INVERSOR,
                                DEMO_INVERSOR_EMAIL,
                                passwordEncoder.encode(DEMO_INVERSOR_PASSWORD),
                                UserRole.ROLE_USER);
                inversor.setCreditsBalance(new BigDecimal("15000.0000"));

                User admin = new User(
                                USER_ADMIN,
                                DEMO_ADMIN_EMAIL,
                                passwordEncoder.encode(DEMO_ADMIN_PASSWORD),
                                UserRole.ROLE_SUPERUSER);
                admin.setCreditsBalance(new BigDecimal("100000.0000"));

                User nuevo = new User(
                                USER_NUEVO,
                                DEMO_NUEVO_EMAIL,
                                passwordEncoder.encode(DEMO_NUEVO_PASSWORD),
                                UserRole.ROLE_USER);
                nuevo.setCreditsBalance(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));

                userRepository.saveAll(List.of(inversor, admin, nuevo));

                return Map.of(
                                USER_INVERSOR, inversor,
                                USER_ADMIN, admin,
                                USER_NUEVO, nuevo);
        }

        private void seedInversorPortfolioAndTransactions(
                        User inversor,
                        List<Player> players,
                        ValuationStrategy strategy,
                        LocalDateTime now) {

                Player haaland = findPlayerByLastName(players, PLAYER_HAALAND);
                Player bellingham = findPlayerByLastName(players, PLAYER_BELLINGHAM);
                Player martinez = findPlayerByLastName(players, PLAYER_MARTINEZ);

                if (haaland == null || bellingham == null || martinez == null) {
                        return;
                }

                int version = strategy.getVersion() != null ? strategy.getVersion() : 1;
                Long actorId = inversor.getId();

                // Secuencia estricta de 6 transacciones en los últimos 45 días
                List<Transaction> transactions = List.of(
                                // Tx 1 (Día -45): Recarga inicial de saldo de 25000.0000 CRD
                                new Transaction(new TransactionData(
                                                actorId, OperationType.RECARGA, null, null, null,
                                                new BigDecimal(INITIAL_BALANCE),
                                                BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                                                new BigDecimal(INITIAL_BALANCE),
                                                null, null, version), now.minusDays(45)),

                                // Tx 3 (Día -30): Compra de 25 tokens de Haaland @ 85.0000 = 2125.0000 CRD
                                new Transaction(new TransactionData(
                                                actorId, OperationType.COMPRA, haaland.getId(), 25,
                                                new BigDecimal("85.0000"),
                                                new BigDecimal("2125.0000"),
                                                new BigDecimal("22150.0000"),
                                                new BigDecimal("20025.0000"),
                                                0, 25, version), now.minusDays(30)),

                                // Tx 4 (Día -20): Compra de 30 tokens de Bellingham @ 80.0000 = 2400.0000 CRD
                                new Transaction(new TransactionData(
                                                actorId, OperationType.COMPRA, bellingham.getId(), 30,
                                                new BigDecimal("80.0000"),
                                                new BigDecimal("2400.0000"),
                                                new BigDecimal("20025.0000"),
                                                new BigDecimal("17625.0000"),
                                                0, 30, version), now.minusDays(20)),

                                // Tx 6 (Día -5): Compra de 149 tokens de Martínez @ 25.0000 = 3725.0000 CRD
                                new Transaction(new TransactionData(
                                                actorId, OperationType.COMPRA, martinez.getId(), 149,
                                                new BigDecimal("25.0000"),
                                                new BigDecimal("3725.0000"),
                                                new BigDecimal("18725.0000"),
                                                new BigDecimal("15000.0000"),
                                                0, 149, version), now.minusDays(5)));

                transactionRepository.saveAll(transactions);

                // Posiciones consolidadas en el portafolio de inversor
                List<PortfolioPosition> positions = List.of(
                                new PortfolioPosition(inversor, haaland, 25, new BigDecimal("85.0000")),
                                new PortfolioPosition(inversor, bellingham, 30, new BigDecimal("80.0000")),
                                new PortfolioPosition(inversor, martinez, 149, new BigDecimal("25.0000")));

                portfolioPositionRepository.saveAll(positions);
        }

        private void seedAdminLiquidity(User admin, List<Player> players) {
                // Garantizar liquidez en el creador de mercado para compras en vivo durante la
                // demo
                List<PortfolioPosition> adminPositions = new ArrayList<>();
                BigDecimal initialCost = new BigDecimal(DEFAULT_PRICE);

                for (Player player : players) {
                        adminPositions.add(new PortfolioPosition(admin, player, 500, initialCost));
                }

                portfolioPositionRepository.saveAll(adminPositions);
        }

        private void seedAlerts(User inversor, List<Player> players, LocalDateTime now) {
                Player martinez = findPlayerByLastName(players, PLAYER_MARTINEZ);
                Player haaland = findPlayerByLastName(players, PLAYER_HAALAND);

                if (martinez == null || haaland == null) {
                        return;
                }

                // Alerta 1 (ACTIVA): Martínez sube por encima de 35.0000 (se disparará al
                // cambiar a BALANCED)
                PriceAlert alert1 = new PriceAlert(inversor, martinez, AlertCondition.ABOVE, new BigDecimal("35.0000"));
                alert1.setStatus(AlertStatus.ACTIVA);

                // Alerta 2 (ACTIVA): Haaland baja por debajo de 100.0000 (se disparará al
                // cambiar a BALANCED)
                PriceAlert alert2 = new PriceAlert(inversor, haaland, AlertCondition.BELOW, new BigDecimal("100.0000"));
                alert2.setStatus(AlertStatus.ACTIVA);

                priceAlertRepository.saveAll(List.of(alert1, alert2, alert3));
        }

        private void seedAdminApiKey(User admin) {
                String rawKey = "cbo_live_demo_admin_api_key_2026_cbo";
                String prefix = rawKey.substring(0, 16);
                String keyHash = hashKey(rawKey);

                ApiKey apiKey = new ApiKey(admin, "Clave API Administrador Demo", keyHash, prefix);
                apiKeyRepository.save(apiKey);
        }

        private Player findPlayerByLastName(List<Player> players, String lastName) {
                return players.stream()
                                .filter(p -> p.getLastName().equalsIgnoreCase(lastName))
                                .findFirst()
                                .orElse(null);
        }

        private String hashKey(String rawKey) {
                try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
                        return HexFormat.of().formatHex(hashBytes);
                } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
                }
        }

        private record PlayerSeedData(
                        String firstName,
                        String lastName,
                        LocalDate birthDate,
                        String nationality,
                        PlayerPosition position,
                        String currentTeam,
                        String league,
                        PlayerStatsDto stats) {
        }
}
