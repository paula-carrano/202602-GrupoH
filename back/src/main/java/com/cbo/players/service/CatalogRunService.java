package com.cbo.players.service;
import com.cbo.players.dto.response.CatalogSyncRunDto;
import com.cbo.players.exception.*;
import com.cbo.players.model.CatalogSyncRun;
import com.cbo.players.repository.CatalogSyncRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogRunService {
    private final CatalogSyncRunRepository runs;
    public CatalogRunService(CatalogSyncRunRepository runs) { this.runs = runs; }
    public Long create(CatalogSyncRun.Origin origin) { return runs.saveAndFlush(new CatalogSyncRun(origin)).getId(); }
    public void recoverInterrupted() {
        for (var run : runs.findByStatus(CatalogSyncRun.Status.RUNNING)) {
            run.finish(CatalogSyncRun.Status.INTERRUPTED);
        }
    }
    public void imported(Long id, CatalogPersistenceService.ImportResult result) {
        find(id).imported(result.created(), result.updated());
    }
    public void statisticsUpdated(Long id) { find(id).statisticsUpdated(); }
    public void error(Long id, String summary) { find(id).error(summary); }
    public void finish(Long id, boolean interrupted) {
        var run = find(id);
        var status = interrupted ? CatalogSyncRun.Status.INTERRUPTED
                : run.getErrorCount() == 0 ? CatalogSyncRun.Status.SUCCESS
                : run.getTeamsProcessed() > 0 || run.getStatisticsUpdated() > 0
                    ? CatalogSyncRun.Status.PARTIAL : CatalogSyncRun.Status.FAILED;
        run.finish(status);
    }
    @Transactional(readOnly = true)
    public CatalogSyncRunDto get(Long id) { return CatalogSyncRunDto.from(find(id)); }
    private CatalogSyncRun find(Long id) {
        return runs.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Ejecucion inexistente", ErrorCode.CATALOG_SYNC_NOT_FOUND));
    }
}
