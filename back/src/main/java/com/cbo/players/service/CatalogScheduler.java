package com.cbo.players.service;

import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.CatalogSyncRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.catalog-sync.enabled", havingValue = "true")
public class CatalogScheduler {

    private static final Logger log = LoggerFactory.getLogger(CatalogScheduler.class);

    private final CatalogSyncService sync;
    private final QuoteCalculationService quoteCalculationService;

    public CatalogScheduler(CatalogSyncService sync) {
        this(sync, null);
    }

    @Autowired
    public CatalogScheduler(CatalogSyncService sync, @Autowired(required = false) QuoteCalculationService quoteCalculationService) {
        this.sync = sync;
        this.quoteCalculationService = quoteCalculationService;
    }

    @Scheduled(cron = "${app.catalog-sync.cron:0 0 15 * * MON}",
            zone = "${app.catalog-sync.zone:America/Argentina/Buenos_Aires}")
    public void synchronize() {
        try {
            sync.start(CatalogSyncRun.Origin.SCHEDULED);
        } catch (ApiException error) {
            if (error.getErrorCode() != ErrorCode.CATALOG_SYNC_BUSY) {
                throw error;
            }
        }

        if (quoteCalculationService != null) {
            try {
                log.info("Iniciando recálculo semanal automático de cotizaciones...");
                quoteCalculationService.recalculateAllQuotes();
            } catch (Exception e) {
                log.error("Error durante el recálculo programado de cotizaciones: {}", e.getMessage(), e);
            }
        }
    }
}
