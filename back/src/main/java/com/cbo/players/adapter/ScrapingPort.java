package com.cbo.players.adapter;

import com.cbo.players.adapter.dto.MatchDto;
import com.cbo.players.adapter.dto.PlayerStatsDto;
import java.time.LocalDate;
import java.util.List;

public interface ScrapingPort {
    java.util.List<com.cbo.players.adapter.dto.CatalogTeamDto> getCompetitionTeams(String competitionCode);
    com.cbo.players.adapter.dto.TeamSquadDto getTeamSquad(long footballDataTeamId);
    /** Receives the external WhoScored ID, not the local player ID. */
    PlayerStatsDto getPlayerStats(long whoscoredId);
    List<MatchDto> getCompetitionMatches(String competitionCode, LocalDate dateFrom, LocalDate dateTo);
    MatchDto getMatch(long matchId);
}
