package com.cbo.players.repository;

import com.cbo.players.model.OperationType;
import com.cbo.players.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByActorUserIdOrderByTimestampDesc(Long actorUserId, Pageable pageable);

    List<Transaction> findByActorUserIdOrderByTimestampDesc(Long actorUserId);

    List<Transaction> findByActorUserId(Long actorUserId);

    List<Transaction> findByPlayerIdOrderByTimestampDesc(Long playerId);

    long countByOperationType(OperationType operationType);

    @Query("SELECT COALESCE(SUM(t.totalAmount), 0) FROM Transaction t WHERE t.operationType IN :types")
    BigDecimal sumTotalAmountByOperationTypes(@Param("types") List<OperationType> types);

    @Query("""
        SELECT t.playerId, SUM(t.quantity), COALESCE(SUM(t.totalAmount), 0)
        FROM Transaction t
        WHERE t.operationType IN :types AND t.playerId IS NOT NULL
        GROUP BY t.playerId
        ORDER BY SUM(t.quantity) DESC, COALESCE(SUM(t.totalAmount), 0) DESC
    """)
    List<Object[]> findTopTradedPlayersRaw(@Param("types") List<OperationType> types, Pageable pageable);
}
