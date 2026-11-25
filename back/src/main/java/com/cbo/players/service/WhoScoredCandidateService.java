package com.cbo.players.service;

import com.cbo.players.adapter.ScrapingPort;
import com.cbo.players.dto.response.WhoScoredCandidateResponseDto;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.exception.ResourceNotFoundException;
import com.cbo.players.model.Player;
import com.cbo.players.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WhoScoredCandidateService {

    private final PlayerRepository playerRepository;
    private final ScrapingPort scraper;

    public WhoScoredCandidateService(PlayerRepository playerRepository, ScrapingPort scraper) {
        this.playerRepository = playerRepository;
        this.scraper = scraper;
    }

    @Transactional(readOnly = true)
    public List<WhoScoredCandidateResponseDto> findCandidates(Long playerId) {
        Player player = playerRepository.findById(playerId).orElseThrow(() ->
                new ResourceNotFoundException("Jugador inexistente", ErrorCode.PLAYER_NOT_FOUND));
        String query = (player.getFirstName() + " " + player.getLastName()).trim();
        if (query.length() > 120) {
            query = query.substring(0, 120).trim();
        }
        return scraper.searchPlayers(query).stream()
                .map(c -> new WhoScoredCandidateResponseDto(c.whoscoredId(), c.name(), c.profileUrl()))
                .toList();
    }
}
