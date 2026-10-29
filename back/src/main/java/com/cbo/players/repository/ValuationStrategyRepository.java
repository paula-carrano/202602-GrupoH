package com.cbo.players.repository;

import com.cbo.players.model.ValuationStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ValuationStrategyRepository extends JpaRepository<ValuationStrategy, Long> {
    Optional<ValuationStrategy> findByActiveTrue();
    Optional<ValuationStrategy> findByCode(String code);
}
