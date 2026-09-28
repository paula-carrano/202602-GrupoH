package com.cbo.players.service;

import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.CatalogSyncRun;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogSchedulerTest {
    @Test void schedulesAndOnlyIgnoresOverlap() {
        var sync = mock(CatalogSyncService.class);
        var scheduler = new CatalogScheduler(sync);
        scheduler.synchronize();
        verify(sync).start(CatalogSyncRun.Origin.SCHEDULED);
        when(sync.start(CatalogSyncRun.Origin.SCHEDULED)).thenThrow(
                new ApiException("busy", HttpStatus.CONFLICT, ErrorCode.CATALOG_SYNC_BUSY));
        assertDoesNotThrow(scheduler::synchronize);
        doThrow(new ApiException("failure", HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED))
                .when(sync).start(CatalogSyncRun.Origin.SCHEDULED);
        assertThrows(ApiException.class, scheduler::synchronize);
    }
}
