package com.cbo.players.model;

import com.cbo.players.adapter.dto.PlayerStatsDto;
import jakarta.persistence.*;
import java.util.Objects;
import java.time.Instant;

@Entity
@Table(name = "player_statistics")
public class PlayerStatistics {
    @Id
    private Long playerId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "player_id")
    private Player player;

    @Column(nullable = false)
    private Long whoscoredId;

    @Column(nullable = false)
    private Instant lastSuccessfulSyncAt;

    @Column(nullable = false)
    private Integer goals = 0;

    @Column(nullable = false)
    private Integer assists = 0;

    @Column(nullable = false)
    private Integer shots = 0;

    @Column(nullable = false)
    private Integer keyPasses = 0;

    @Column(nullable = false)
    private Integer dribbles = 0;

    @Column(nullable = false)
    private Integer tackles = 0;

    @Column(nullable = false)
    private Double rating = 0.0;

    @Column(nullable = false)
    private Integer minutosJugados = 0;

    @Column(nullable = false)
    private Integer tarjetasAmarillas = 0;

    @Column(nullable = false)
    private Integer tarjetasRojas = 0;

    public PlayerStatistics() {}

    public PlayerStatistics(Player player) {
        this.player = player;
        this.whoscoredId = 0L;
        this.lastSuccessfulSyncAt = Instant.now();
    }

    public PlayerStatistics(Player player, Long whoscoredId, PlayerStatsDto stats) {
        Objects.requireNonNull(stats, "stats");
        this.player = player;
        update(whoscoredId != null ? whoscoredId : 0L, stats);
    }

    public void update(Long sourceId, PlayerStatsDto stats) {
        whoscoredId = sourceId;
        lastSuccessfulSyncAt = Instant.now();
        goals = stats.goals();
        assists = stats.assists();
        shots = stats.shots();
        keyPasses = stats.keyPasses();
        dribbles = stats.dribbles();
        tackles = stats.tackles();
        rating = stats.rating();
        minutosJugados = stats.minutosJugados();
        tarjetasAmarillas = stats.tarjetasAmarillas();
        tarjetasRojas = stats.tarjetasRojas();
    }

    public Long getPlayerId() { return playerId; }
    public Player getPlayer() { return player; }
    public Long getWhoscoredId() { return whoscoredId; }
    public Instant getLastSuccessfulSyncAt() { return lastSuccessfulSyncAt; }
    public Integer getGoals() { return goals; }
    public Integer getAssists() { return assists; }
    public Integer getShots() { return shots; }
    public Integer getKeyPasses() { return keyPasses; }
    public Integer getDribbles() { return dribbles; }
    public Integer getTackles() { return tackles; }
    public Double getRating() { return rating; }
    public Integer getMinutosJugados() { return minutosJugados; }
    public Integer getTarjetasAmarillas() { return tarjetasAmarillas; }
    public Integer getTarjetasRojas() { return tarjetasRojas; }

    public PlayerStatsDto metrics() {
        return new PlayerStatsDto(goals, assists, shots, keyPasses, dribbles, tackles, rating, minutosJugados, tarjetasAmarillas, tarjetasRojas);
    }
}
