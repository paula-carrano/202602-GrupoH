const browserPool = require('./browserPool');
const teamMatcher = require('./teamMatcher');
const config = require('../../config/env');
const { withRetry } = require('../../utils/retry');
const {
  MatchNotFoundError,
  ScrapeTimeoutError,
  ScrapeBlockedError
} = require('../../utils/errors');

/**
 * Parses match lineup HTML string and extracts lineups for both teams.
 * Strategy 1: Check for embedded matchCentreData in <script> blocks (WhoScored Live Match Centre).
 * Strategy 2: Legacy fallback using CSS selectors/DOM attributes.
 */
const parseLineupFromHtml = (html, matchDate = '') => {
  if (!html || typeof html !== 'string') {
    return {
      source: 'WHOSCORED',
      date: matchDate,
      homeTeam: { name: '', formation: 'Unknown', startingXI: [], bench: [] },
      awayTeam: { name: '', formation: 'Unknown', startingXI: [], bench: [] }
    };
  }

  // Strategy 1: Check embedded matchCentreData script block
  // e.g.: var matchCentreData = { ... }; or matchCentreData = { ... };
  const matchCentreRegex = /(?:var\s+matchCentreData|matchCentreData)\s*=\s*(\{[\s\S]*?\});/;
  const matchCentreMatch = html.match(matchCentreRegex);

  if (matchCentreMatch) {
    try {
      const jsonStr = matchCentreMatch[1];
      const data = Function('"use strict";return (' + jsonStr + ')')();

      if (data && (data.home || data.away)) {
        const parseSide = (sideData) => {
          if (!sideData) return { name: '', formation: 'Unknown', startingXI: [], bench: [] };

          const name = sideData.name || '';
          const formation = sideData.formations && sideData.formations.length > 0
            ? sideData.formations[0].formationName || 'Unknown'
            : sideData.formation || 'Unknown';

          const startingXI = [];
          const bench = [];

          if (Array.isArray(sideData.players)) {
            for (const p of sideData.players) {
              const entry = {
                id: p.playerId !== undefined ? String(p.playerId) : (p.id !== undefined ? String(p.id) : ''),
                name: p.name || p.knownName || '',
                shirtNumber: parseInt(p.shirtNo || p.shirtNumber || p.jerseyNumber || 0, 10),
                position: p.position || p.field || (p.isFirstEleven ? 'Starter' : 'Sub')
              };

              if (p.isFirstEleven) {
                startingXI.push(entry);
              } else {
                bench.push(entry);
              }
            }
          }

          return {
            name,
            formation: String(formation),
            startingXI,
            bench
          };
        };

        return {
          source: 'WHOSCORED',
          date: matchDate || (data.startTime ? data.startTime.substring(0, 10) : ''),
          homeTeam: parseSide(data.home),
          awayTeam: parseSide(data.away)
        };
      }
    } catch (e) {
      // Fallback to DOM strategy
    }
  }

  // Strategy 2: Legacy mock / DOM parsing
  const getTeamChunk = (className) => {
    if (className === 'home') {
      const match = html.match(/<div class="home">([\s\S]*?)<div class="away">/i);
      return match ? match[1] : '';
    } else {
      const match = html.match(/<div class="away">([\s\S]*?)<\/body>/i);
      return match ? match[1] : '';
    }
  };

  const parseTeamBlock = (teamClass) => {
    const chunk = getTeamChunk(teamClass);
    if (!chunk) return { name: '', formation: 'Unknown', startingXI: [], bench: [] };

    const formationMatch = chunk.match(/class="formation">([^<]+)<\/span>/i);
    const nameMatch = chunk.match(/class="team-name">([^<]+)<\/span>/i);

    const parseSection = (sectionName) => {
      const startTag = `<div class="${sectionName}">`;
      const startIndex = chunk.indexOf(startTag);
      if (startIndex === -1) return [];

      const afterStart = chunk.substring(startIndex + startTag.length);
      // It ends at either `<div class="substitutes">` or `</div>\s*</div>` (end of team)
      const nextSectionMatch = afterStart.match(/<div class="substitutes">|<\/div>\s*<\/div>/i);
      const sectionHtml = nextSectionMatch ? afterStart.substring(0, nextSectionMatch.index) : afterStart;

      const playerRegex = /data-player-id="([^"]+)"\s+data-player-name="([^"]+)"\s+data-player-number="([^"]+)"\s+data-position="([^"]+)"/gi;
      const players = [];
      let p;
      while ((p = playerRegex.exec(sectionHtml)) !== null) {
        players.push({
          id: p[1],
          name: p[2],
          shirtNumber: parseInt(p[3], 10) || 0,
          position: p[4]
        });
      }
      return players;
    };

    return {
      name: nameMatch ? nameMatch[1].trim() : '',
      formation: formationMatch ? formationMatch[1].trim() : 'Unknown',
      startingXI: parseSection('starting-lineup'),
      bench: parseSection('substitutes')
    };
  };

  return {
    source: 'WHOSCORED',
    date: matchDate,
    homeTeam: parseTeamBlock('home'),
    awayTeam: parseTeamBlock('away')
  };
};

/**
 * Scrapes WhoScored lineup with resilient match discovery (search + date fallback) and retry policy
 */
const scrapeLineup = async (homeTeam, awayTeam, date, abortSignal = null) => {
  return browserPool.schedule(async () => {
    return withRetry(
      async () => {
        let page = null;
        try {
          page = await browserPool.acquirePage();

          if (abortSignal && abortSignal.aborted) {
            throw new Error('ABORTED');
          }

          if (abortSignal) {
            abortSignal.addEventListener('abort', async () => {
              try {
                if (page && !page.isClosed()) await page.close();
              } catch (e) {}
            });
          }

          let matchUrl = null;

          // 1. Intentar descubrir el partido mediante búsqueda directa en WhoScored Search
          const searchQuery = encodeURIComponent(`${homeTeam} ${awayTeam}`);
          const searchUrl = `https://www.whoscored.com/Search/?q=${searchQuery}`;

          try {
            const searchRes = await page.goto(searchUrl, {
              waitUntil: 'domcontentloaded',
              timeout: config.SCRAPE_TIMEOUT_MS
            });

            if (searchRes && (searchRes.status() === 403 || searchRes.status() === 429)) {
              throw new ScrapeBlockedError();
            }

            const searchHtml = await page.content();
            if (searchHtml.includes('Attention Required! | Cloudflare') || searchHtml.includes('cf-browser-verification')) {
              throw new ScrapeBlockedError();
            }

            matchUrl = teamMatcher.findMatchUrlFromSearchHtml(searchHtml, homeTeam, awayTeam);
          } catch (err) {
            if (err instanceof ScrapeBlockedError) throw err;
            // Si la búsqueda falla o da 404, continuar al fallback por cartelera/fecha
          }

          // 2. Si no se encontró en la búsqueda, intentar fallback en la cartelera por fecha
          if (!matchUrl && date) {
            const fixturesUrl = `https://www.whoscored.com/Matches?date=${date}`;
            try {
              const fixturesRes = await page.goto(fixturesUrl, {
                waitUntil: 'domcontentloaded',
                timeout: config.SCRAPE_TIMEOUT_MS
              });

              if (fixturesRes && (fixturesRes.status() === 403 || fixturesRes.status() === 429)) {
                throw new ScrapeBlockedError();
              }

              const fixturesHtml = await page.content();
              if (fixturesHtml.includes('Attention Required! | Cloudflare') || fixturesHtml.includes('cf-browser-verification')) {
                throw new ScrapeBlockedError();
              }

              matchUrl = teamMatcher.findMatchUrlFromFixturesHtml(fixturesHtml, homeTeam, awayTeam);
            } catch (err) {
              if (err instanceof ScrapeBlockedError) throw err;
            }
          }

          if (!matchUrl) {
            throw new MatchNotFoundError(`No se encontró el partido entre ${homeTeam} y ${awayTeam} para la fecha ${date} en WhoScored.`);
          }

          // 3. Navegar al detalle del partido para extraer alineaciones
          const fullMatchUrl = matchUrl.startsWith('http') ? matchUrl : `https://www.whoscored.com${matchUrl}`;
          let matchRes;
          try {
            matchRes = await page.goto(fullMatchUrl, {
              waitUntil: 'domcontentloaded',
              timeout: config.SCRAPE_TIMEOUT_MS
            });
          } catch (err) {
            if (err.name === 'TimeoutError') throw new ScrapeTimeoutError();
            throw err;
          }

          if (matchRes && (matchRes.status() === 403 || matchRes.status() === 429)) {
            throw new ScrapeBlockedError();
          }

          const matchHtml = await page.content();
          return parseLineupFromHtml(matchHtml, date);
        } catch (err) {
          if (err instanceof MatchNotFoundError || err instanceof ScrapeBlockedError) {
            throw err; // Non-retryable
          }
          if (err.message && err.message.includes('timeout')) {
            throw new ScrapeTimeoutError();
          }
          throw err;
        } finally {
          if (page && !page.isClosed()) {
            try {
              await page.close();
            } catch (e) {}
          }
        }
      },
      config.MAX_RETRIES,
      1000,
      (err) => !(err instanceof MatchNotFoundError) && !(err instanceof ScrapeBlockedError) && err.message !== 'ABORTED'
    );
  });
};

module.exports = {
  parseLineupFromHtml,
  scrapeLineup
};
