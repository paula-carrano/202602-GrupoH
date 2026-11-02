package com.cbo.players.unit;

import com.cbo.players.config.DemoDataSeeder;
import com.cbo.players.model.*;
import com.cbo.players.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.core.env.Environment;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerStatisticsRepository playerStatisticsRepository;

    @Mock
    private PlayerQuoteRepository playerQuoteRepository;

    @Mock
    private PortfolioPositionRepository portfolioPositionRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PriceAlertRepository priceAlertRepository;

    @Mock
    private ValuationStrategyRepository valuationStrategyRepository;

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Environment environment;

    @InjectMocks
    private DemoDataSeeder demoDataSeeder;

    @Test
    @DisplayName("Cuando el usuario inversor ya existe, el seeder no realiza ninguna acción (idempotencia)")
    void runWhenInversorAlreadyExistsDoesNothing() {
        when(userRepository.existsByEmail("inversor@demo.com")).thenReturn(true);

        demoDataSeeder.run(new DefaultApplicationArguments(new String[] {}));

        verifyNoInteractions(environment);
        verify(userRepository, never()).saveAll(any());
        verify(playerRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Cuando la base está vacía, el seeder inicializa estrategias, jugadores, cotizaciones, usuarios y alertas")
    void runWhenEmptyDatabaseExecutesFullSeed() {
        when(userRepository.existsByEmail("inversor@demo.com")).thenReturn(false);
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.empty());
        when(playerRepository.count()).thenReturn(0L);

        when(playerRepository.saveAndFlush(any(Player.class))).thenAnswer(invocation -> {
            Player p = invocation.getArgument(0);
            p.setId(10L);
            return p;
        });

        stubDemoConfiguration();
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");

        demoDataSeeder.run(new DefaultApplicationArguments(new String[] {}));

        verify(valuationStrategyRepository).saveAll(anyList());
        verify(playerRepository, atLeast(9)).saveAndFlush(any(Player.class));
        verify(playerStatisticsRepository, atLeast(9)).save(any(PlayerStatistics.class));
        verify(playerQuoteRepository).saveAll(anyList());
        verify(userRepository).saveAll(anyList());
        verify(transactionRepository).saveAll(anyList());
        verify(portfolioPositionRepository, times(2)).saveAll(anyList());
        verify(priceAlertRepository).saveAll(anyList());
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("Cuando los jugadores ya existen y hay estrategia activa, utiliza los jugadores existentes")
    void runWhenPlayersAndStrategyAlreadyExistUsesExistingData() {
        when(userRepository.existsByEmail("inversor@demo.com")).thenReturn(false);

        ValuationStrategy activeStrategy = new ValuationStrategy();
        activeStrategy.setId(1L);
        activeStrategy.setCode("OFFENSIVE");
        activeStrategy.setName("Rendimiento Ofensivo");
        activeStrategy.setActive(true);
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(activeStrategy));

        Player haaland = new Player("Erling", "Haaland", LocalDate.of(2000, 7, 21), "Norway",
                PlayerPosition.FORWARD, "Manchester City", "Premier League");
        haaland.setId(2L);

        Player bellingham = new Player("Jude", "Bellingham", LocalDate.of(2003, 6, 29), "England",
                PlayerPosition.MIDFIELDER, "Real Madrid", "La Liga");
        bellingham.setId(3L);

        Player martinez = new Player("Emiliano", "Martínez", LocalDate.of(1992, 9, 2), "Argentina",
                PlayerPosition.GOALKEEPER, "Aston Villa", "Premier League");
        martinez.setId(4L);

        when(playerRepository.count()).thenReturn(4L);
        when(playerRepository.findAllByActiveTrue()).thenReturn(List.of(haaland, bellingham, martinez));
        stubDemoConfiguration();
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");

        demoDataSeeder.run(new DefaultApplicationArguments(new String[] {}));

        verify(playerRepository, never()).saveAndFlush(any(Player.class));
        verify(playerQuoteRepository).saveAll(anyList());
        verify(userRepository).saveAll(anyList());
        verify(transactionRepository).saveAll(anyList());
        verify(priceAlertRepository).saveAll(anyList());
        verify(apiKeyRepository).save(any(ApiKey.class));
    }
    private void stubDemoConfiguration() {
        when(environment.getProperty(anyString())).thenReturn("test-only-demo-credential");
    }

    static Stream<Arguments> invalidConfiguration() {
        return Stream.of("DEMO_INVERSOR_PASSWORD", "DEMO_ADMIN_PASSWORD",
                        "DEMO_NUEVO_PASSWORD", "DEMO_ADMIN_API_KEY")
                .flatMap(name -> Stream.of(Arguments.of(name, null), Arguments.of(name, ""),
                        Arguments.of(name, "   ")));
    }

    @ParameterizedTest
    @MethodSource("invalidConfiguration")
    void runWhenConfigurationMissingOrBlankFailsBeforeInserting(String name, String value) {
        when(environment.getProperty(anyString())).thenAnswer(invocation ->
                name.equals(invocation.getArgument(0)) ? value : "test-only-demo-credential");

        DefaultApplicationArguments arguments = new DefaultApplicationArguments(new String[] {});
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> demoDataSeeder.run(arguments));

        assertTrue(error.getMessage().contains(name));
        verifyNoSeedWrites();
    }

    @Test
    void runWhenApiKeyTooShortFailsBeforeInserting() {
        when(environment.getProperty(anyString())).thenAnswer(invocation ->
                "DEMO_ADMIN_API_KEY".equals(invocation.getArgument(0)) ? "short" : "test-password");

        DefaultApplicationArguments arguments = new DefaultApplicationArguments(new String[] {});
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> demoDataSeeder.run(arguments));

        assertTrue(error.getMessage().contains("16 caracteres"));
        verifyNoSeedWrites();
    }

    private void verifyNoSeedWrites() {
        verify(userRepository).existsByEmail("inversor@demo.com");
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(playerRepository, playerStatisticsRepository, playerQuoteRepository,
                portfolioPositionRepository, transactionRepository, priceAlertRepository,
                valuationStrategyRepository, apiKeyRepository, passwordEncoder);
    }
}
