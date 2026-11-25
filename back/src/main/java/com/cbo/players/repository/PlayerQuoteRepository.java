package com.cbo.players.repository;

import com.cbo.players.model.PlayerQuote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    @Query("""
        SELECT pq FROM PlayerQuote pq
        JOIN FETCH pq.player p
        WHERE p.active = true
          AND pq.id = (
              SELECT MAX(sub.id) FROM PlayerQuote sub
              WHERE sub.player.id = pq.player.id
                AND sub.timestamp = (
                    SELECT MAX(sub2.timestamp) FROM PlayerQuote sub2 WHERE sub2.player.id = pq.player.id
                )
          )
    """)
    List<PlayerQuote> findLatestQuotesForActivePlayers();

    @Query("SELECT MAX(pq.timestamp) FROM PlayerQuote pq")
    Optional<LocalDateTime> findMaxTimestamp();
}
