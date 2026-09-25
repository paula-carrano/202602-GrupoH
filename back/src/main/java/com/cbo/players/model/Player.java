package com.cbo.players.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 200)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 200)
    private String lastName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 50)
    private String nationality;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PlayerPosition position;

    @Column(name = "current_team", nullable = false, length = 80)
    private String currentTeam;

    @Column(nullable = false, length = 80)
    private String league;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PlayerQuote> quotes = new ArrayList<>();

    public Player() {
    }

    @Column(name = "football_data_id", unique = true)
    private Long footballDataId;
    public Long getFootballDataId() { return footballDataId; }
    public void setFootballDataId(Long value) { footballDataId = value; }
    @Column(name = "football_data_team_id")
    private Long footballDataTeamId;
    public Long getFootballDataTeamId() { return footballDataTeamId; }
    public void setFootballDataTeamId(Long value) { footballDataTeamId = value; }
    @Column(name = "whoscored_id", unique = true)
    private Long whoscoredId;
    public Long getWhoscoredId() { return whoscoredId; }
    public void setWhoscoredId(Long value) { whoscoredId = value; }
    @Column(name = "last_catalog_sync_at")
    private java.time.Instant lastCatalogSyncAt;
    public java.time.Instant getLastCatalogSyncAt() { return lastCatalogSyncAt; }
    public void setLastCatalogSyncAt(java.time.Instant value) { lastCatalogSyncAt = value; }

    public Player(String firstName, String lastName, LocalDate birthDate, String nationality,
                  PlayerPosition position, String currentTeam, String league) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.nationality = nationality;
        this.position = position;
        this.currentTeam = currentTeam;
        this.league = league;
        this.active = true;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public PlayerPosition getPosition() {
        return position;
    }

    public void setPosition(PlayerPosition position) {
        this.position = position;
    }

    public String getCurrentTeam() {
        return currentTeam;
    }

    public void setCurrentTeam(String currentTeam) {
        this.currentTeam = currentTeam;
    }

    public String getLeague() {
        return league;
    }

    public void setLeague(String league) {
        this.league = league;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<PlayerQuote> getQuotes() {
        return quotes;
    }

    public void setQuotes(List<PlayerQuote> quotes) {
        this.quotes = quotes;
    }
}
