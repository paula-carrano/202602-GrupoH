package com.cbo.players.repository;

import com.cbo.players.model.PlayerQuote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerQuoteRepository extends JpaRepository<PlayerQuote, Long> {

    List<PlayerQuote> findByPlayerIdOrderByTimestampDesc(Long playerId);

    Optional<PlayerQuote> findFirstByPlayerIdOrderByTimestampDesc(Long playerId);

    Page<PlayerQuote> findByPlayerIdOrderByTimestampDesc(Long playerId, Pageable pageable);

    Optional<PlayerQuote> findFirstByPlayerIdAndTimestampLessThanEqualOrderByTimestampDesc(
            Long playerId,
            LocalDateTime timestamp
    );

    @Query("""
        SELECT pq FROM PlayerQuote pq
        WHERE pq.timestamp = (
            SELECT MAX(sub.timestamp) FROM PlayerQuote sub WHERE sub.player.id = pq.player.id
        )
    """)
    List<PlayerQuote> findLatestQuotesForAllPlayers();
}
