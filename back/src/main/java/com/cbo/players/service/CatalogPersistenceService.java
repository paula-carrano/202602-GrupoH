package com.cbo.players.service;

import com.cbo.players.adapter.dto.*;
import com.cbo.players.dto.response.StoredPlayerStatsDto;
import com.cbo.players.exception.*;
import com.cbo.players.model.*;
import com.cbo.players.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.List;

@Service
public class CatalogPersistenceService {
    private final PlayerRepository players;
    private final PlayerStatisticsRepository statistics;
    public CatalogPersistenceService(PlayerRepository players, PlayerStatisticsRepository statistics) {
        this.players = players; this.statistics = statistics;
    }
    public record ImportResult(int created, int updated) {}
    public record LinkedPlayer(Long playerId, Long whoscoredId) {}
    public record LinkResult(Long playerId, Long whoscoredId) {}

    @Transactional
    public ImportResult importSquad(String league, TeamSquadDto squad) {
        int created = 0, updated = 0;
        for (SquadPlayerDto source : squad.players()) {
            Player player = players.findByFootballDataId(source.id()).orElse(null);
            if (player == null) {
                player = new Player();
                player.setFootballDataId(source.id());
                created++;
            } else {
                player = players.findLockedById(player.getId()).orElseThrow();
                updated++;
            }
            boolean separateNames = source.firstName() != null && !source.firstName().isBlank()
                    && source.lastName() != null && !source.lastName().isBlank();
            player.setFirstName(separateNames ? source.firstName() : source.name());
            player.setLastName(separateNames ? source.lastName() : "");
            player.setBirthDate(source.birthDate());
            player.setNationality(source.nationality());
            player.setPosition(position(source.position()));
            player.setCurrentTeam(squad.team().name());
            player.setLeague(league);
            player.setFootballDataTeamId(squad.team().id());
            player.setLastCatalogSyncAt(Instant.now());
            players.save(player);
        }
        players.flush();
        return new ImportResult(created, updated);
    }

    static PlayerPosition position(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "")) {
            case "goalkeeper" -> PlayerPosition.GOALKEEPER;
            case "defence", "defense", "defender", "centreback", "centerback", "leftback", "rightback" -> PlayerPosition.DEFENDER;
            case "midfield", "midfielder", "centralmidfield", "defensivemidfield", "attackingmidfield", "leftmidfield", "rightmidfield" -> PlayerPosition.MIDFIELDER;
            case "offence", "offense", "forward", "attack", "attacker", "striker", "centreforward", "centerforward", "secondstriker", "leftwinger", "rightwinger" -> PlayerPosition.FORWARD;
            default -> null;
        };
    }

    @Transactional(readOnly = true)
    public List<LinkedPlayer> linkedPlayers() {
        return players.findAllByActiveTrueAndWhoscoredIdIsNotNull().stream()
                .map(p -> new LinkedPlayer(p.getId(), p.getWhoscoredId())).toList();
    }

    @Transactional
    public boolean saveStatistics(LinkedPlayer link, PlayerStatsDto stats) {
        Player player = players.findLockedById(link.playerId()).orElse(null);
        // The manual association may have changed while HTTP was in progress.
        if (player == null || !player.isActive() || !Objects.equals(player.getWhoscoredId(), link.whoscoredId())) return false;
        PlayerStatistics stored = statistics.findById(player.getId()).orElseGet(() -> new PlayerStatistics(player));
        stored.update(link.whoscoredId(), stats);
        statistics.saveAndFlush(stored);
        return true;
    }

    @Transactional
    public LinkResult link(Long playerId, Long whoscoredId) {
        if (whoscoredId != null && whoscoredId <= 0) {
            throw new ApiException("whoscoredId debe ser positivo", HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED);
        }
        Player player = players.findLockedById(playerId).orElseThrow(() ->
                new ResourceNotFoundException("Jugador inexistente", ErrorCode.PLAYER_NOT_FOUND));
        if (whoscoredId != null && players.existsByWhoscoredIdAndIdNot(whoscoredId, playerId)) {
            throw new ApiException("WhoScored ID ya vinculado", HttpStatus.CONFLICT, ErrorCode.WHOSCORED_ID_CONFLICT);
        }
        if (!Objects.equals(player.getWhoscoredId(), whoscoredId)) {
            statistics.findById(playerId).ifPresent(statistics::delete);
            statistics.flush();
            player.setWhoscoredId(whoscoredId);
            players.flush();
        }
        return new LinkResult(playerId, player.getWhoscoredId());
    }

    @Transactional(readOnly = true)
    public StoredPlayerStatsDto statistics(Long playerId) {
        if (!players.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador inexistente", ErrorCode.PLAYER_NOT_FOUND);
        }
        return StoredPlayerStatsDto.from(statistics.findById(playerId).orElseThrow(() ->
                new ResourceNotFoundException("Sin estadisticas guardadas", ErrorCode.PLAYER_STATISTICS_NOT_FOUND)));
    }
}
