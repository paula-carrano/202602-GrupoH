package com.cbo.players.model;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "catalog_sync_runs")
public class CatalogSyncRun {
    public enum Status { RUNNING, SUCCESS, PARTIAL, FAILED, INTERRUPTED }
    public enum Origin { MANUAL, SCHEDULED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Status status = Status.RUNNING;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Origin origin;
    @Column(nullable = false)
    private Instant startedAt = Instant.now();
    private Instant finishedAt;
    private int teamsProcessed;
    private int playersCreated;
    private int playersUpdated;
    private int statisticsUpdated;
    private int errorCount;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "catalog_sync_errors", joinColumns = @JoinColumn(name = "run_id"))
    @OrderColumn(name = "error_index")
    @Column(name = "error_summary", length = 300)
    private List<String> errors = new ArrayList<>();
    public CatalogSyncRun() {}
    public CatalogSyncRun(Origin origin) { this.origin = origin; }
    public void imported(int created, int updated) {
        teamsProcessed++; playersCreated += created; playersUpdated += updated;
    }
    public void statisticsUpdated() { statisticsUpdated++; }
    public void error(String summary) {
        errorCount++;
        if (errors.size() < 100) errors.add(summary);
    }
    public void finish(Status result) { status = result; finishedAt = Instant.now(); }
    public Long getId() { return id; }
    public Status getStatus() { return status; }
    public Origin getOrigin() { return origin; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getTeamsProcessed() { return teamsProcessed; }
    public int getPlayersCreated() { return playersCreated; }
    public int getPlayersUpdated() { return playersUpdated; }
    public int getStatisticsUpdated() { return statisticsUpdated; }
    public int getErrorCount() { return errorCount; }
    public List<String> getErrors() { return List.copyOf(errors); }
}
