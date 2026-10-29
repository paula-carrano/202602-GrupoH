package com.cbo.players.service;

import com.cbo.players.dto.response.PortfolioPositionDto;
import com.cbo.players.dto.response.PortfolioResponseDto;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerQuote;
import com.cbo.players.model.PortfolioPosition;
import com.cbo.players.model.User;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PortfolioPositionRepository;
import com.cbo.players.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PortfolioService {

    private final UserRepository userRepository;
    private final PortfolioPositionRepository portfolioPositionRepository;
    private final PlayerQuoteRepository playerQuoteRepository;

    public PortfolioService(UserRepository userRepository,
                            PortfolioPositionRepository portfolioPositionRepository,
                            PlayerQuoteRepository playerQuoteRepository) {
        this.userRepository = userRepository;
        this.portfolioPositionRepository = portfolioPositionRepository;
        this.playerQuoteRepository = playerQuoteRepository;
    }

    public PortfolioResponseDto getUserPortfolio(Long targetUserId, Long actorUserId, boolean isPrivileged) {
        if (!targetUserId.equals(actorUserId) && !isPrivileged) {
            throw new ApiException("Acceso denegado al portfolio de otro usuario", HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED);
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException("Usuario no encontrado con ID: " + targetUserId, HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND));

        List<PortfolioPosition> positions = portfolioPositionRepository.findByUserIdAndTokensQuantityGreaterThan(targetUserId, 0);

        List<PortfolioPositionDto> positionDtos = new ArrayList<>();
        BigDecimal totalInvestedCost = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalCurrentValue = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        for (PortfolioPosition pos : positions) {
            Player player = pos.getPlayer();
            PlayerQuote latestQuote = playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(player.getId()).orElse(null);
            BigDecimal currentQuote = latestQuote != null ? latestQuote.getPrice() : BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

            int tokens = pos.getTokensQuantity();
            BigDecimal costBasis = pos.getAverageBuyPrice().multiply(BigDecimal.valueOf(tokens)).setScale(4, RoundingMode.HALF_UP);
            BigDecimal currentValue = currentQuote.multiply(BigDecimal.valueOf(tokens)).setScale(4, RoundingMode.HALF_UP);
            BigDecimal pnlNominal = currentValue.subtract(costBasis).setScale(4, RoundingMode.HALF_UP);

            BigDecimal pnlPercentage = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
                pnlPercentage = pnlNominal.divide(costBasis, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(4, RoundingMode.HALF_UP);
            }

            totalInvestedCost = totalInvestedCost.add(costBasis);
            totalCurrentValue = totalCurrentValue.add(currentValue);

            String playerName = player.getFirstName() + " " + player.getLastName();
            String position = player.getPosition() != null ? player.getPosition().name() : "";

            positionDtos.add(new PortfolioPositionDto(
                    player.getId(),
                    playerName,
                    position,
                    player.getCurrentTeam(),
                    tokens,
                    pos.getAverageBuyPrice(),
                    currentQuote,
                    currentValue,
                    costBasis,
                    pnlNominal,
                    pnlPercentage
            ));
        }

        BigDecimal totalPnLNominal = totalCurrentValue.subtract(totalInvestedCost).setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalPnLPercentage = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        if (totalInvestedCost.compareTo(BigDecimal.ZERO) > 0) {
            totalPnLPercentage = totalPnLNominal.divide(totalInvestedCost, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal creditsBalance = user.getCreditsBalance() != null
                ? user.getCreditsBalance().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        return new PortfolioResponseDto(
                user.getId(),
                creditsBalance,
                totalInvestedCost,
                totalCurrentValue,
                totalPnLNominal,
                totalPnLPercentage,
                positionDtos
        );
    }
}
