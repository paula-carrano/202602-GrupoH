package com.cbo.players.repository;

import com.cbo.players.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
    java.util.Optional<Player> findByFootballDataId(Long id);
    boolean existsByWhoscoredIdAndIdNot(Long whoscoredId, Long id);
    List<Player> findAllByActiveTrueAndWhoscoredIdIsNotNull();
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Player p where p.id = :id")
    java.util.Optional<Player> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    List<Player> findAllByActiveTrue();
}
