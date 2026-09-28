package com.cbo.players.model;
import com.cbo.players.adapter.dto.PlayerStatsDto;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "player_statistics")
public class PlayerStatistics {
    @Id
    private Long playerId;
    @OneToOne(fetch = FetchType.LAZY) @MapsId @JoinColumn(name = "player_id")
    private Player player;
    @Column(nullable = false)
    private Long whoscoredId;
    @Column(nullable = false)
    private Instant lastSuccessfulSyncAt;
    @Column(nullable = false)
    private Integer goals;
    @Column(nullable = false)
    private Integer assists;
    @Column(nullable = false)
    private Integer shots;
    @Column(nullable = false)
    private Integer keyPasses;
    @Column(nullable = false)
    private Integer dribbles;
    @Column(nullable = false)
    private Integer tackles;
    @Column(nullable = false)
    private Double rating;
    @Column(nullable = false)
    private Integer minutosJugados;
    @Column(nullable = false)
    private Integer tarjetasAmarillas;
    @Column(nullable = false)
    private Integer tarjetasRojas;
    public PlayerStatistics() {}
    public PlayerStatistics(Player player) { this.player = player; }
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
    public Long getWhoscoredId() { return whoscoredId; }
    public Instant getLastSuccessfulSyncAt() { return lastSuccessfulSyncAt; }
    public PlayerStatsDto metrics() { return new PlayerStatsDto(goals, assists, shots, keyPasses, dribbles, tackles, rating, minutosJugados, tarjetasAmarillas, tarjetasRojas); }
}
