package com.cbo.players.repository;
import com.cbo.players.model.CatalogSyncRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CatalogSyncRunRepository extends JpaRepository<CatalogSyncRun, Long> {
    List<CatalogSyncRun> findByStatus(CatalogSyncRun.Status status);
}
