package com.cbo.players.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhoScoredPlayerCandidateDto(Long whoscoredId, String name, String profileUrl) {
    public WhoScoredPlayerCandidateDto {
        if (whoscoredId == null || whoscoredId <= 0 || name == null || name.isBlank()
                || profileUrl == null || profileUrl.isBlank()) {
            throw new IllegalArgumentException("Invalid WhoScored player candidate");
        }
    }
}
