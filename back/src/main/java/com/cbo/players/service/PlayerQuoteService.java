package com.cbo.players.service;

import com.cbo.players.dto.response.DateEffectiveQuoteResponseDto;
import com.cbo.players.dto.response.PagedResponseDto;
import com.cbo.players.dto.response.PlayerQuoteResponseDto;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerQuote;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PlayerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PlayerQuoteService {

    private final PlayerRepository playerRepository;
    private final PlayerQuoteRepository playerQuoteRepository;

    public PlayerQuoteService(PlayerRepository playerRepository,
                              PlayerQuoteRepository playerQuoteRepository) {
        this.playerRepository = playerRepository;
        this.playerQuoteRepository = playerQuoteRepository;
    }

    public PagedResponseDto<PlayerQuoteResponseDto> getPaginatedQuotes(Long playerId, int page, int size) {
        ensurePlayerExists(playerId);

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<PlayerQuote> quotePage = playerQuoteRepository.findByPlayerIdOrderByTimestampDesc(playerId, pageable);
        Page<PlayerQuoteResponseDto> dtoPage = quotePage.map(PlayerQuoteResponseDto::from);

        return PagedResponseDto.from(dtoPage);
    }

    public DateEffectiveQuoteResponseDto getQuoteByDate(Long playerId, LocalDate date) {
        ensurePlayerExists(playerId);

        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        Optional<PlayerQuote> quoteOpt = playerQuoteRepository
                .findFirstByPlayerIdAndTimestampLessThanEqualOrderByTimestampDesc(playerId, endOfDay);

        if (quoteOpt.isPresent()) {
            PlayerQuote q = quoteOpt.get();
            return new DateEffectiveQuoteResponseDto(
                    q.getId(),
                    playerId,
                    date.toString(),
                    q.getPrice(),
                    q.getCurrency(),
                    q.getStrategyName(),
                    q.getStrategyVersion(),
                    q.getTimestamp()
            );
        }

        // Default initial quote baseline when queried date precedes any recorded quote
        return new DateEffectiveQuoteResponseDto(
                null,
                playerId,
                date.toString(),
                new BigDecimal("1.0000"),
                "CRD",
                "Valoración Base Inicial",
                1,
                null
        );
    }

    private Player ensurePlayerExists(Long playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> new ApiException(
                        "Jugador no encontrado con ID: " + playerId,
                        HttpStatus.NOT_FOUND,
                        ErrorCode.PLAYER_NOT_FOUND
                ));
    }
}
