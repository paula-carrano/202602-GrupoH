package com.cbo.players.adapter;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.LocalDate;

import static com.cbo.players.adapter.ScrapingException.Kind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ScrapingRestAdapterTest {
    private static final String STATS = """
            {"goals":12,"assists":8,"shots":45,"keyPasses":32,"dribbles":27,"tackles":14,
             "rating":7.65,"minutosJugados":1840,"tarjetasAmarillas":2,"tarjetasRojas":0,"extra":true}
            """;
    private static final String MATCH = """
            {"id":438120,"competition":"PL","utcDate":"2026-09-20T16:30:00Z","status":"FUTURE_STATUS",
             "matchday":null,"homeTeam":{"id":null,"name":"Arsenal","extra":1},
             "awayTeam":{"id":61,"name":"Chelsea"},
             "score":{"winner":null,"duration":"REGULAR","fullTime":{"home":null,"away":null},
             "halfTime":{"home":1,"away":0}},"extra":"ignored"}
            """;
    private MockRestServiceServer server;
    private RestClient client;
    private ScrapingRestAdapter adapter;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://scraper:3000");
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
        adapter = createAdapter("internal-test-key");
    }

    private ScrapingRestAdapter createAdapter(String key) {
        return new ScrapingRestAdapter(client,
                JsonMapper.builder().addModule(new JavaTimeModule()).build(), key);
    }

    private void expect(String path, String body) {
        server.expect(requestTo("http://scraper:3000" + path))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("X-API-Key", "internal-test-key"))
                .andExpect(headerDoesNotExist("X-Auth-Token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void readsAllPlayerMetrics() {
        expect("/players/123/stats", STATS);
        var stats = adapter.getPlayerStats(123);
        assertAll(
                () -> assertEquals(12, stats.goals()), () -> assertEquals(8, stats.assists()),
                () -> assertEquals(45, stats.shots()), () -> assertEquals(32, stats.keyPasses()),
                () -> assertEquals(27, stats.dribbles()), () -> assertEquals(14, stats.tackles()),
                () -> assertEquals(7.65, stats.rating()), () -> assertEquals(1840, stats.minutosJugados()),
                () -> assertEquals(2, stats.tarjetasAmarillas()), () -> assertEquals(0, stats.tarjetasRojas()));
        server.verify();
    }

    @Test
    void readsMatchWithNullsAndUnknownStatus() {
        expect("/matches/438120", MATCH);
        var match = adapter.getMatch(438120);
        assertEquals(438120L, match.id());
        assertEquals(Instant.parse("2026-09-20T16:30:00Z"), match.utcDate());
        assertEquals("FUTURE_STATUS", match.status());
        assertNull(match.matchday());
        assertNull(match.homeTeam().id());
        assertNull(match.score().winner());
        assertNull(match.score().fullTime().home());
        assertEquals(1, match.score().halfTime().home());
        server.verify();
    }

    @Test
    void acceptsNullScore() {
        expect("/matches/1", """
                {"id":1,"competition":"PL","utcDate":"2026-09-20T16:30:00Z","status":"SCHEDULED",
                 "homeTeam":{"id":1,"name":"A"},"awayTeam":{"id":2,"name":"B"},"score":null}
                """);
        assertNull(adapter.getMatch(1).score());
        server.verify();
    }

    @Test
    void readsListAndNormalizesCompetition() {
        expect("/competitions/PL/matches?dateFrom=2026-09-01&dateTo=2026-09-25", "[" + MATCH + "]");
        var matches = adapter.getCompetitionMatches("pl", LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-25"));
        assertEquals(1, matches.size());
        assertEquals(438120L, matches.get(0).id());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PL", "BL1", "PD", "SA", "FL1"})
    void acceptsEmptyListsAndOmitsAbsentDates(String code) {
        expect("/competitions/" + code + "/matches", "[]");
        assertTrue(adapter.getCompetitionMatches(code, null, null).isEmpty());
        server.verify();
    }

    @Test
    void acceptsEitherDateIndependently() {
        expect("/competitions/PL/matches?dateFrom=2026-09-01", "[]");
        expect("/competitions/PL/matches?dateTo=2026-09-01", "[]");
        var date = LocalDate.parse("2026-09-01");
        adapter.getCompetitionMatches("PL", date, null);
        adapter.getCompetitionMatches("PL", null, date);
        server.verify();
    }

    @Test
    void rejectsInvalidInputsBeforeHttp() {
        assertThrows(IllegalArgumentException.class, () -> adapter.getPlayerStats(0));
        assertThrows(IllegalArgumentException.class, () -> adapter.getPlayerStats(-1));
        assertThrows(IllegalArgumentException.class, () -> adapter.getMatch(0));
        assertThrows(IllegalArgumentException.class, () -> adapter.getMatch(-1));
        assertThrows(IllegalArgumentException.class, () -> adapter.getCompetitionMatches(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> adapter.getCompetitionMatches("INVALID", null, null));
        assertThrows(IllegalArgumentException.class, () -> adapter.getCompetitionMatches("PL",
                LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 1)));
        server.verify();
    }

    @Test
    void rejectsMissingKeyBeforeHttp() {
        for (String key : new String[] {null, "", " "}) {
            var error = assertThrows(ScrapingException.class, () -> createAdapter(key).getMatch(1));
            assertEquals(CONFIGURATION, error.getKind());
        }
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"400,INVALID_REQUEST_PARAMS", "401,UNAUTHORIZED", "404,PLAYER_NOT_FOUND",
            "404,MATCH_NOT_FOUND", "429,RATE_LIMIT_EXCEEDED", "502,SCRAPE_BLOCKED",
            "502,EXTERNAL_API_AUTH_ERROR", "504,SCRAPE_TIMEOUT", "503,NEW_REMOTE_CODE",
            "503,new-remote-code"})
    void preservesHttpStatusAndCodeWithoutRetry(int status, String code) {
        server.expect(requestTo("http://scraper:3000/matches/1"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"" + code + "\",\"message\":\"secret remote details\"}"));
        var error = assertThrows(ScrapingException.class, () -> adapter.getMatch(1));
        assertEquals(REMOTE_HTTP, error.getKind());
        assertEquals(status, error.getRemoteStatus());
        assertEquals(code, error.getRemoteCode());
        assertFalse(error.toString().contains("secret remote details"));
        assertNull(error.getCause());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html>bad gateway</html>", "{}", "null", "{\"error\":123}", ""})
    void handlesUnparseableRemoteErrors(String body) {
        server.expect(requestTo("http://scraper:3000/matches/1"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body(body));
        var error = assertThrows(ScrapingException.class, () -> adapter.getMatch(1));
        assertEquals(502, error.getRemoteStatus());
        assertEquals("REMOTE_HTTP_ERROR", error.getRemoteCode());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null", "{}", "[]", "not json", "{\"goals\":1}"})
    void rejectsInvalidStats(String body) {
        expect("/players/1/stats", body);
        assertEquals(INVALID_RESPONSE,
                assertThrows(ScrapingException.class, () -> adapter.getPlayerStats(1)).getKind());
        server.verify();
    }

    @Test
    void rejectsIncompatibleNumericFields() {
        expect("/players/1/stats", STATS.replace("\"goals\":12", "\"goals\":1.5"));
        assertEquals(INVALID_RESPONSE,
                assertThrows(ScrapingException.class, () -> adapter.getPlayerStats(1)).getKind());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[null]", "[{}]", "", "[] trailing"})
    void rejectsInvalidMatchLists(String body) {
        expect("/competitions/PL/matches", body);
        assertEquals(INVALID_RESPONSE, assertThrows(ScrapingException.class,
                () -> adapter.getCompetitionMatches("PL", null, null)).getKind());
        server.verify();
    }

    @Test
    void distinguishesConnectionFailure() {
        server.expect(requestTo("http://scraper:3000/matches/1"))
                .andRespond(withException(new ConnectException("refused")));
        assertEquals(CONNECTION, assertThrows(ScrapingException.class, () -> adapter.getMatch(1)).getKind());
        server.verify();
    }

    @Test
    void distinguishesTimeout() {
        server.expect(requestTo("http://scraper:3000/matches/1"))
                .andRespond(withException(new SocketTimeoutException("timeout")));
        assertEquals(TIMEOUT, assertThrows(ScrapingException.class, () -> adapter.getMatch(1)).getKind());
        server.verify();
    }

    @Test
    void distinguishesTimeoutWhileReadingResponseBody() {
        server.expect(requestTo("http://scraper:3000/matches/1")).andRespond(request -> {
            var stream = new java.io.InputStream() {
                @Override
                public int read() throws java.io.IOException {
                    throw new SocketTimeoutException("read timeout");
                }
            };
            return new org.springframework.mock.http.client.MockClientHttpResponse(stream, HttpStatus.OK);
        });
        assertEquals(TIMEOUT, assertThrows(ScrapingException.class, () -> adapter.getMatch(1)).getKind());
        server.verify();
    }

    @Test
    void readsCatalogTeamsAndSquads() {
        expect("/competitions/PL/teams", "[{\"id\":57,\"name\":\"Arsenal\"}]");
        expect("/teams/57/squad", """
                {"team":{"id":57,"name":"Arsenal"},"players":[
                {"id":1,"name":"Full Name","birthDate":"2000-01-01","position":null}]}
                """);
        assertEquals(57L,adapter.getCompetitionTeams("pl").get(0).id());
        var squad = adapter.getTeamSquad(57);
        assertEquals(LocalDate.of(2000,1,1),squad.players().get(0).birthDate());
        assertNull(squad.players().get(0).nationality());
        server.verify();
    }

    @Test
    void acceptsEmptyCatalogDataAndRejectsInvalidIdentifiers() {
        expect("/competitions/PL/teams", "[]");
        expect("/teams/57/squad", "{\"team\":{\"id\":57,\"name\":\"Arsenal\"},\"players\":[]}");
        assertTrue(adapter.getCompetitionTeams("PL").isEmpty());
        assertTrue(adapter.getTeamSquad(57).players().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> adapter.getTeamSquad(0));
        assertThrows(IllegalArgumentException.class, () -> adapter.getCompetitionTeams("bad"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"team\":{\"id\":57,\"name\":\"A\"}}",
        "{\"team\":{\"id\":58,\"name\":\"A\"},\"players\":[]}",
        "{\"team\":{\"id\":57,\"name\":\"A\"},\"players\":[null]}"})
    void rejectsInvalidSquads(String body) {
        expect("/teams/57/squad",body);
        assertEquals(INVALID_RESPONSE,assertThrows(ScrapingException.class, () -> adapter.getTeamSquad(57)).getKind());
        server.verify();
    }
}
