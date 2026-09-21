package com.cbo.players.repository;

import com.cbo.players.model.PlayerQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlayerQuoteRepository extends JpaRepository<PlayerQuote, Long> {
    List<PlayerQuote> findByPlayerIdOrderByTimestampDesc(Long playerId);
}
