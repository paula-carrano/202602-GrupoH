package com.cbo.players.repository;

import com.cbo.players.model.AlertStatus;
import com.cbo.players.model.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    List<PriceAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PriceAlert> findByStatus(AlertStatus status);

    List<PriceAlert> findByPlayerIdAndStatus(Long playerId, AlertStatus status);

    Optional<PriceAlert> findByIdAndUserId(Long id, Long userId);
}
