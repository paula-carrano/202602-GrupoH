package com.cbo.players.service;

import com.cbo.players.dto.response.PlayerRankingResponseDto;
import com.cbo.players.dto.response.RankingItemDto;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerQuote;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.ValuationStrategyRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RankingService {

    private final PlayerRepository playerRepository;
    private final PlayerQuoteRepository playerQuoteRepository;
    private final ValuationStrategyRepository valuationStrategyRepository;

    public RankingService(PlayerRepository playerRepository,
                          PlayerQuoteRepository playerQuoteRepository,
                          ValuationStrategyRepository valuationStrategyRepository) {
        this.playerRepository = playerRepository;
        this.playerQuoteRepository = playerQuoteRepository;
        this.valuationStrategyRepository = valuationStrategyRepository;
    }

    @Cacheable(cacheNames = "players::ranking", key = "'active_ranking'")
    public PlayerRankingResponseDto getRanking() {
        ValuationStrategy activeStrategy = valuationStrategyRepository.findByActiveTrue().orElse(null);
        String strategyCode = activeStrategy != null ? activeStrategy.getCode() : "BALANCED";
        int strategyVersion = activeStrategy != null && activeStrategy.getVersion() != null ? activeStrategy.getVersion() : 1;

        List<Player> activePlayers = playerRepository.findAll().stream()
                .filter(Player::isActive)
                .toList();

        record PlayerWithQuote(Player player, BigDecimal quote) {}

        List<PlayerWithQuote> list = activePlayers.stream()
                .map(p -> {
                    BigDecimal quote = playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(p.getId())
                            .map(PlayerQuote::getPrice)
                            .orElse(BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP));
                    return new PlayerWithQuote(p, quote);
                })
                .sorted((a, b) -> b.quote().compareTo(a.quote()))
                .toList();

        List<RankingItemDto> rankingItems = new ArrayList<>();
        int rank = 1;
        for (PlayerWithQuote item : list) {
            Player p = item.player();
            String pos = p.getPosition() != null ? p.getPosition().name() : "";
            rankingItems.add(new RankingItemDto(
                    rank++,
                    p.getId(),
                    p.getFirstName(),
                    p.getLastName(),
                    pos,
                    p.getCurrentTeam(),
                    p.getLeague(),
                    item.quote(),
                    "CRD"
            ));
        }

        return new PlayerRankingResponseDto(strategyCode, strategyVersion, true, rankingItems);
    }
}
