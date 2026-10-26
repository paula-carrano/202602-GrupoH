package com.cbo.players.unit;

import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.OperationType;
import com.cbo.players.model.Transaction;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.repository.TransactionRepository;
import com.cbo.players.repository.ValuationStrategyRepository;
import com.cbo.players.service.ValuationStrategyService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValuationStrategyServiceTest {

    @Mock
    private ValuationStrategyRepository valuationStrategyRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private ValuationStrategyService valuationStrategyService;

    private ValuationStrategy strategy1;
    private ValuationStrategy strategy2;

    @BeforeEach
    void setUp() {
        strategy1 = new ValuationStrategy(
                "OFFENSIVE", "Rendimiento Ofensivo", "Desc 1",
                1, true, new BigDecimal("1.0000"),
                1.5, 1.0, 0.005, 0.2, 0.3, 1.2, -0.3
        );
        strategy1.setId(1L);

        strategy2 = new ValuationStrategy(
                "BALANCED", "Rendimiento Balanceado", "Desc 2",
                1, false, new BigDecimal("1.0000"),
                1.0, 0.8, 0.01, 0.1, 0.2, 1.5, -0.5
        );
        strategy2.setId(2L);
    }

    @Test
    @DisplayName("initializeDefaultStrategies guarda estrategias por defecto si la base está vacía")
    void initializeDefaultStrategies_savesDefaultsWhenEmpty() {
        when(valuationStrategyRepository.count()).thenReturn(0L);

        valuationStrategyService.initializeDefaultStrategies();

        verify(valuationStrategyRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("initializeDefaultStrategies no realiza acciones si ya existen estrategias")
    void initializeDefaultStrategies_doesNothingWhenAlreadyPopulated() {
        when(valuationStrategyRepository.count()).thenReturn(2L);

        valuationStrategyService.initializeDefaultStrategies();

        verify(valuationStrategyRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("getActiveStrategy retorna la estrategia activa si existe")
    void getActiveStrategy_returnsActiveStrategy() {
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(strategy1));

        ValuationStrategy active = valuationStrategyService.getActiveStrategy();

        assertThat(active).isNotNull();
        assertThat(active.getCode()).isEqualTo("OFFENSIVE");
        assertThat(active.isActive()).isTrue();
    }

    @Test
    @DisplayName("getActiveStrategy lanza ApiException 404 si no hay estrategia activa")
    void getActiveStrategy_throwsWhenNotFound() {
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> valuationStrategyService.getActiveStrategy())
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.STRATEGY_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("getStrategyById retorna la estrategia correspondiente")
    void getStrategyById_returnsStrategy() {
        when(valuationStrategyRepository.findById(2L)).thenReturn(Optional.of(strategy2));

        ValuationStrategy result = valuationStrategyService.getStrategyById(2L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getCode()).isEqualTo("BALANCED");
    }

    @Test
    @DisplayName("getStrategyById lanza ApiException 404 si el ID no existe")
    void getStrategyById_throwsWhenNotFound() {
        when(valuationStrategyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> valuationStrategyService.getStrategyById(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.STRATEGY_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("getAllStrategies retorna el listado completo")
    void getAllStrategies_returnsAll() {
        when(valuationStrategyRepository.findAll()).thenReturn(List.of(strategy1, strategy2));

        List<ValuationStrategy> all = valuationStrategyService.getAllStrategies();

        assertThat(all).hasSize(2).containsExactly(strategy1, strategy2);
    }

    @Test
    @DisplayName("activateStrategy activa la estrategia seleccionada, desactiva las previas y registra la transacción")
    void activateStrategy_activatesTargetAndDeactivatesOthers() {
        when(valuationStrategyRepository.findById(2L)).thenReturn(Optional.of(strategy2));
        List<ValuationStrategy> list = new ArrayList<>(List.of(strategy1, strategy2));
        when(valuationStrategyRepository.findAll()).thenReturn(list);

        ValuationStrategy activated = valuationStrategyService.activateStrategy(2L, 5L);

        assertThat(activated.isActive()).isTrue();
        assertThat(strategy1.isActive()).isFalse();
        assertThat(strategy2.isActive()).isTrue();

        verify(valuationStrategyRepository).saveAll(list);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction tx = txCaptor.getValue();
        assertThat(tx.getActorUserId()).isEqualTo(5L);
        assertThat(tx.getOperationType()).isEqualTo(OperationType.CAMBIO_ESTRATEGIA);
        assertThat(tx.getStrategyVersion()).isEqualTo(strategy2.getVersion());
    }

    @Test
    @DisplayName("activateStrategy con actorUserId nulo utiliza el ID por defecto 1L")
    void activateStrategy_withNullActorUsesDefaultId() {
        when(valuationStrategyRepository.findById(1L)).thenReturn(Optional.of(strategy1));
        when(valuationStrategyRepository.findAll()).thenReturn(List.of(strategy1));

        valuationStrategyService.activateStrategy(1L, null);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getActorUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("activateStrategy lanza ApiException 404 si la estrategia a activar no existe")
    void activateStrategy_throwsWhenNotFound() {
        when(valuationStrategyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> valuationStrategyService.activateStrategy(99L, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.STRATEGY_NOT_FOUND);
                });
    }
}
