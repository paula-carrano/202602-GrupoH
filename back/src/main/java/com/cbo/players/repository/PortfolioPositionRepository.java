package com.cbo.players.repository;

import com.cbo.players.model.PortfolioPosition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {

    Optional<PortfolioPosition> findByUserIdAndPlayerId(Long userId, Long playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PortfolioPosition p WHERE p.user.id = :userId AND p.player.id = :playerId")
    Optional<PortfolioPosition> findByUserIdAndPlayerIdWithLock(@Param("userId") Long userId, @Param("playerId") Long playerId);

    List<PortfolioPosition> findByUserId(Long userId);

    List<PortfolioPosition> findByUserIdAndTokensQuantityGreaterThan(Long userId, Integer minTokens);

    @Query("SELECT COALESCE(SUM(p.tokensQuantity), 0) FROM PortfolioPosition p WHERE p.player.id = :playerId")
    Integer sumTokensByPlayerId(@Param("playerId") Long playerId);
}
