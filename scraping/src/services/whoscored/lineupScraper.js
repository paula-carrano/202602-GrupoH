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
 * Parses match lineup HTML string and extracts lineups for both teams
 */
const parseLineupFromHtml = (html, matchDate = '') => {
  const parseTeamBlock = (teamClass) => {
    const blockRegex = new RegExp(`<div class="${teamClass}">([\\s\\S]*?)<\\/div>\\s*<\\/div>`, 'i');
    const match = html.match(blockRegex);
    const content = match ? match[1] : html;

    const formationMatch = content.match(/class="formation">([^<]+)<\/span>/i);
    const nameMatch = content.match(/class="team-name">([^<]+)<\/span>/i);

    const parsePlayers = (sectionClass) => {
      const sectionRegex = new RegExp(`<div class="${sectionClass}">([\\s\\S]*?)<\\/div>`, 'i');
      const secMatch = content.match(sectionRegex);
      if (!secMatch) return [];

      const playerRegex = /data-player-id="([^"]+)"\s+data-player-name="([^"]+)"\s+data-player-number="([^"]+)"\s+data-position="([^"]+)"/gi;
      const players = [];
      let p;
      while ((p = playerRegex.exec(secMatch[1])) !== null) {
        players.push({
          id: parseInt(p[1], 10) || p[1],
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
      startingXI: parsePlayers('starting-lineup'),
      bench: parsePlayers('substitutes')
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
 * Scrapes WhoScored lineup with team matching, date fallback and retry policy
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

          // 1. Navegar a la página de partidos de la fecha en WhoScored
          const fixturesUrl = `https://www.whoscored.com/Matches?date=${date}`;
          let fixturesRes;
          try {
            fixturesRes = await page.goto(fixturesUrl, {
              waitUntil: 'domcontentloaded',
              timeout: config.SCRAPE_TIMEOUT_MS
            });
          } catch (err) {
            if (err.name === 'TimeoutError') throw new ScrapeTimeoutError();
            throw err;
          }

          if (fixturesRes && (fixturesRes.status() === 403 || fixturesRes.status() === 429)) {
            throw new ScrapeBlockedError();
          }

          const fixturesHtml = await page.content();
          if (fixturesHtml.includes('Attention Required! | Cloudflare') || fixturesHtml.includes('cf-browser-verification')) {
            throw new ScrapeBlockedError();
          }

          // 2. Buscar URL del partido específico por nombres de equipos
          const matchUrl = teamMatcher.findMatchUrlFromFixturesHtml(fixturesHtml, homeTeam, awayTeam);
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
