package com.cbo.players.adapter;

import com.cbo.players.adapter.dto.MatchDto;
import com.cbo.players.adapter.dto.PlayerStatsDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import static com.cbo.players.adapter.ScrapingException.Kind.*;

public final class ScrapingRestAdapter implements ScrapingPort {
    private static final Set<String> COMPETITIONS = Set.of("PL", "BL1", "PD", "SA", "FL1");
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String apiKey;

    public ScrapingRestAdapter(RestClient client, ObjectMapper mapper, String apiKey) {
        this.client = client;
        this.mapper = mapper.copy()
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.apiKey = apiKey;
    }

    @Override
    public PlayerStatsDto getPlayerStats(long whoscoredId) {
        requirePositive(whoscoredId);
        return get(uri -> uri.path("/players/{id}/stats").build(whoscoredId), new TypeReference<>() {});
    }

    @Override
    public List<MatchDto> getCompetitionMatches(String competitionCode, LocalDate dateFrom, LocalDate dateTo) {
        String code = competitionCode == null ? "" : competitionCode.toUpperCase(Locale.ROOT);
        if (!COMPETITIONS.contains(code)) {
            throw new IllegalArgumentException("Unsupported competition code");
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("dateFrom must not be after dateTo");
        }
        List<MatchDto> matches = get(uri -> {
            uri.path("/competitions/{code}/matches");
            if (dateFrom != null) uri.queryParam("dateFrom", dateFrom);
            if (dateTo != null) uri.queryParam("dateTo", dateTo);
            return uri.build(code);
        }, new TypeReference<>() {});
        if (matches.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalidResponse();
        }
        return List.copyOf(matches);
    }

    @Override
    public MatchDto getMatch(long matchId) {
        requirePositive(matchId);
        return get(uri -> uri.path("/matches/{id}").build(matchId), new TypeReference<>() {});
    }

    @Override
    public List<com.cbo.players.adapter.dto.CatalogTeamDto> getCompetitionTeams(String competitionCode) {
        String code = competitionCode == null ? "" : competitionCode.toUpperCase(Locale.ROOT);
        if (!COMPETITIONS.contains(code)) throw new IllegalArgumentException("Unsupported competition code");
        List<com.cbo.players.adapter.dto.CatalogTeamDto> teams = get(
                uri -> uri.path("/competitions/{code}/teams").build(code), new TypeReference<>() {});
        if (teams.stream().anyMatch(java.util.Objects::isNull)) throw invalidResponse();
        return List.copyOf(teams);
    }

    @Override
    public com.cbo.players.adapter.dto.TeamSquadDto getTeamSquad(long footballDataTeamId) {
        requirePositive(footballDataTeamId);
        com.cbo.players.adapter.dto.TeamSquadDto squad = get(
                uri -> uri.path("/teams/{id}/squad").build(footballDataTeamId), new TypeReference<>() {});
        if (squad.team().id() != footballDataTeamId) throw invalidResponse();
        return squad;
    }

    private <T> T get(Function<UriBuilder, URI> uri, TypeReference<T> type) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ScrapingException(CONFIGURATION, "SCRAPER_API_KEY is not configured");
        }
        try {
            String body = client.get().uri(uri).header("X-API-Key", apiKey)
                    .retrieve().body(String.class);
            if (body == null || body.isBlank()) throw invalidResponse();
            T value = mapper.readValue(body, type);
            if (value == null) throw invalidResponse();
            return value;
        } catch (RestClientResponseException ex) {
            throw new ScrapingException(REMOTE_HTTP, "Scraper returned an HTTP error",
                    ex.getStatusCode().value(), remoteCode(ex.getResponseBodyAsString()));
        } catch (ResourceAccessException ex) {
            throw new ScrapingException(isTimeout(ex) ? TIMEOUT : CONNECTION,
                    isTimeout(ex) ? "Scraper request timed out" : "Cannot connect to scraper");
        } catch (RestClientException ex) {
            if (isTimeout(ex)) {
                throw new ScrapingException(TIMEOUT, "Scraper request timed out");
            }
            throw invalidResponse();
        } catch (JsonProcessingException ex) {
            throw invalidResponse();
        }
    }

    private String remoteCode(String body) {
        try {
            var node = mapper.readTree(body);
            var code = node == null ? null : node.get("error");
            if (code != null && code.isTextual() && !code.asText().isBlank()) {
                return code.asText();
            }
        } catch (JsonProcessingException ignored) {
            // Do not expose remote bodies or authentication details.
        }
        return "REMOTE_HTTP_ERROR";
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) return true;
        }
        return false;
    }

    private static void requirePositive(long id) {
        if (id <= 0) throw new IllegalArgumentException("External ID must be positive");
    }

    private static ScrapingException invalidResponse() {
        return new ScrapingException(INVALID_RESPONSE, "Scraper returned an invalid response");
    }
}
