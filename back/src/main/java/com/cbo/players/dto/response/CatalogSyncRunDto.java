package com.cbo.players.dto.response;
import com.cbo.players.model.CatalogSyncRun;
import java.time.Instant;
import java.util.List;
public record CatalogSyncRunDto(Long id, CatalogSyncRun.Status status, CatalogSyncRun.Origin origin,
        Instant startedAt, Instant finishedAt, int teamsProcessed, int playersCreated,
        int playersUpdated, int statisticsUpdated, int errorCount, List<String> errors) {
    public static CatalogSyncRunDto from(CatalogSyncRun r) {
        return new CatalogSyncRunDto(r.getId(), r.getStatus(), r.getOrigin(), r.getStartedAt(),
                r.getFinishedAt(), r.getTeamsProcessed(), r.getPlayersCreated(), r.getPlayersUpdated(),
                r.getStatisticsUpdated(), r.getErrorCount(), r.getErrors());
    }
}
