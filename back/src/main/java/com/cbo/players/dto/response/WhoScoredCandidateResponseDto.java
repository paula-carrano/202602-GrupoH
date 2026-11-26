package com.cbo.players.dto.response;

public record WhoScoredCandidateResponseDto(
        Long whoscoredId,
        String name,
        String profileUrl
) {
}
