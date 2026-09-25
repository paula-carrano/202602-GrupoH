package com.cbo.players.service;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.CatalogSyncRun;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.catalog-sync.enabled", havingValue = "true")
public class CatalogScheduler {
    private final CatalogSyncService sync;
    public CatalogScheduler(CatalogSyncService sync) { this.sync = sync; }
    @Scheduled(cron = "${app.catalog-sync.cron:0 0 15 * * MON}",
            zone = "${app.catalog-sync.zone:America/Argentina/Buenos_Aires}")
    public void synchronize() {
        try { sync.start(CatalogSyncRun.Origin.SCHEDULED); }
        catch (ApiException error) {
            if (error.getErrorCode() != ErrorCode.CATALOG_SYNC_BUSY) throw error;
        }
    }
}
