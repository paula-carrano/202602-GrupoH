package com.cbo.players.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"app.scraper.api-key=", "app.scraper.base-url=http://127.0.0.1:1"})
@ActiveProfiles("test")
class ScrapingContextTest {
    @Autowired
    private ScrapingPort scraping;

    @Test
    void backendStartsWithoutKeyOrRunningScraper() {
        assertNotNull(scraping);
        var error = assertThrows(ScrapingException.class, () -> scraping.getMatch(1));
        assertEquals(ScrapingException.Kind.CONFIGURATION, error.getKind());
    }
}
