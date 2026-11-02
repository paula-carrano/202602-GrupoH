package com.cbo.players.service;

import com.cbo.players.model.records.ValuationWeights;
import com.cbo.players.model.records.TransactionData;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.OperationType;
import com.cbo.players.model.Transaction;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.repository.TransactionRepository;
import com.cbo.players.repository.ValuationStrategyRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ValuationStrategyService {

    private static final Logger log = LoggerFactory.getLogger(ValuationStrategyService.class);

    private final ValuationStrategyRepository valuationStrategyRepository;
    private final TransactionRepository transactionRepository;

    public ValuationStrategyService(
            ValuationStrategyRepository valuationStrategyRepository,
            TransactionRepository transactionRepository) {
        this.valuationStrategyRepository = valuationStrategyRepository;
        this.transactionRepository = transactionRepository;
    }

    @PostConstruct
    @Transactional
    public void initializeDefaultStrategies() {
        if (valuationStrategyRepository.count() == 0) {
            log.info("Inicializando estrategias de valoración por defecto en la base de datos.");

            ValuationStrategy offensive = new ValuationStrategy(
                    "OFFENSIVE",
                    "Rendimiento Ofensivo",
                    "Ponderación orientada al rendimiento ofensivo y definición",
                    1,
                    true,
                    new BigDecimal("1.0000"), new ValuationWeights(
                            1.5,
                            1.0,
                            0.005,
                            0.2,
                            0.3,
                            1.2,
                            -0.3));

            ValuationStrategy balanced = new ValuationStrategy(
                    "BALANCED",
                    "Rendimiento Global Equilibrado",
                    "Ponderación integral de estadísticas ofensivas, minutos y disciplina",
                    1,
                    false,
                    new BigDecimal("1.0000"), new ValuationWeights(
                            1.0,
                            0.8,
                            0.01,
                            0.1,
                            0.2,
                            1.5,
                            -0.5));

            valuationStrategyRepository.saveAll(List.of(offensive, balanced));
        }
    }

    @Transactional(readOnly = true)
    public ValuationStrategy getActiveStrategy() {
        return valuationStrategyRepository.findByActiveTrue()
                .orElseThrow(() -> new ApiException(
                        "No se encontró una estrategia de valoración activa configurada en el sistema.",
                        HttpStatus.NOT_FOUND,
                        ErrorCode.STRATEGY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ValuationStrategy getStrategyById(Long id) {
        return valuationStrategyRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        String.format("No se encontró la estrategia de valoración con ID %d.", id),
                        HttpStatus.NOT_FOUND,
                        ErrorCode.STRATEGY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<ValuationStrategy> getAllStrategies() {
        return valuationStrategyRepository.findAll();
    }

    @Transactional
    public ValuationStrategy activateStrategy(Long id, Long actorUserId) {
        ValuationStrategy target = valuationStrategyRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        String.format("No se encontró la estrategia de valoración con ID %d.", id),
                        HttpStatus.NOT_FOUND,
                        ErrorCode.STRATEGY_NOT_FOUND));

        List<ValuationStrategy> allStrategies = valuationStrategyRepository.findAll();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        for (ValuationStrategy strategy : allStrategies) {
            if (strategy.getId().equals(id)) {
                strategy.setActive(true);
                strategy.setUpdatedAt(now);
            } else if (strategy.isActive()) {
                strategy.setActive(false);
                strategy.setUpdatedAt(now);
            }
        }
        valuationStrategyRepository.saveAll(allStrategies);

        // Registro auditable e inmutable de cambio de estrategia
        Transaction tx = new Transaction(new TransactionData(
                actorUserId != null ? actorUserId : 1L,
                OperationType.CAMBIO_ESTRATEGIA,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                target.getVersion()));
        transactionRepository.save(tx);

        log.info("Estrategia [{}] (ID {}) activada por usuario {}", sanitizeForLog(target.getCode()), target.getId(),
                sanitizeForLog(String.valueOf(actorUserId)));
        return target;
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }

        return value.replaceAll("[\\r\\n\\t]", "_");
    }
}
