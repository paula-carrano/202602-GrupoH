const browserPool = require('./browserPool');
const config = require('../../config/env');
const { toInt, toFloat } = require('../../utils/normalizer');
const { withRetry } = require('../../utils/retry');
const {
  PlayerNotFoundError,
  ScrapeTimeoutError,
  ScrapeBlockedError
} = require('../../utils/errors');

/**
 * Parses raw HTML string extracted from WhoScored and returns normalized PlayerStats object
 */
const parsePlayerStatsFromHtml = (html) => {
  if (!html || typeof html !== 'string') {
    return {
      goals: 0,
      assists: 0,
      shots: 0,
      keyPasses: 0,
      dribbles: 0,
      tackles: 0,
      rating: 0.0,
      minutosJugados: 0,
      tarjetasAmarillas: 0,
      tarjetasRojas: 0
    };
  }

  // Strategy 1: Check for WhoScored embedded DataStore / require.config.params['args']
  // In live WhoScored pages, tournament statistics and detailed totals are rendered via Client-Side Templates
  // using data injected in require.config.params['args'] = { tournaments: [...], ... };
  const scriptRegex = /require\.config\.params\['args'\]\s*=\s*(\{[\s\S]*?\});/;
  const scriptMatch = html.match(scriptRegex);

  if (scriptMatch) {
    try {
      const jsonStr = scriptMatch[1];
      // Safely evaluate JS object literal
      const argsData = Function('"use strict";return (' + jsonStr + ')')();

      if (argsData && Array.isArray(argsData.tournaments) && argsData.tournaments.length > 0) {
        let totalGoals = 0;
        let totalAssists = 0;
        let totalShots = 0;
        let totalKeyPasses = 0;
        let totalDribbles = 0;
        let totalTackles = 0;
        let totalYellow = 0;
        let totalRed = 0;
        let weightedRatingSum = 0;
        let totalApps = 0;

        for (const t of argsData.tournaments) {
          totalGoals += toInt(t.Goals);
          totalAssists += toInt(t.Assists);
          totalShots += toInt(t.TotalShots);
          totalKeyPasses += toInt(t.KeyPasses);
          totalDribbles += toInt(t.Dribbles);
          totalTackles += toInt(t.TotalTackles);
          totalYellow += toInt(t.Yellow) + toInt(t.SecondYellow);
          totalRed += toInt(t.Red);

          const apps = toInt(t.GameStarted) + toInt(t.SubOn);
          totalApps += apps;
          const r = toFloat(t.Rating);
          weightedRatingSum += r * apps;
        }

        const avgRating = totalApps > 0 ? toFloat(weightedRatingSum / totalApps) : 0.0;

        // Sum minutes played across match incidents / matches table if present
        let totalMins = 0;
        const minRegex = /class="[^"]*col-data-mins[^"]*"[^>]*>\s*(\d+)'?\s*<\/div>/gi;
        let minMatch;
        while ((minMatch = minRegex.exec(html)) !== null) {
          totalMins += toInt(minMatch[1]);
        }

        return {
          goals: totalGoals,
          assists: totalAssists,
          shots: totalShots,
          keyPasses: totalKeyPasses,
          dribbles: totalDribbles,
          tackles: totalTackles,
          rating: avgRating,
          minutosJugados: totalMins,
          tarjetasAmarillas: totalYellow,
          tarjetasRojas: totalRed
        };
      }
    } catch (e) {
      // Fallback to table extraction if JSON/object parsing fails
    }
  }

  // Strategy 2: Legacy / Server-rendered HTML table extraction
  const getCell = (colName) => {
    const headerRegex = new RegExp(`<th>\\s*${colName}\\s*</th>`, 'i');
    const headerMatch = html.match(headerRegex);
    if (!headerMatch) return null;

    const tableMatch = html.substring(headerMatch.index);
    const tbodyMatch = tableMatch.match(/<tbody>[\s\S]*?<tr>([\s\S]*?)<\/tr>/i);
    if (!tbodyMatch) return null;

    const theadMatch = html.substring(0, headerMatch.index);
    const prevThs = theadMatch.match(/<th>/gi) || [];
    const colIndex = prevThs.length;

    const tds = tbodyMatch[1].match(/<td>([\s\S]*?)<\/td>/gi) || [];
    if (tds[colIndex]) {
      return tds[colIndex].replace(/<\/?td>/gi, '').trim();
    }
    return null;
  };

  const mins = getCell('Mins');
  const goals = getCell('Goals');
  const assists = getCell('Assists');
  const yel = getCell('Yel');
  const red = getCell('Red');
  const rating = getCell('Rating');

  const parseClassValue = (className) => {
    const regex = new RegExp(`class="${className}">([^<]*)<`, 'i');
    const match = html.match(regex);
    return match ? match[1].trim() : null;
  };

  const shots = parseClassValue('shots');
  const keyPasses = parseClassValue('key-passes');
  const dribbles = parseClassValue('dribbles');
  const tackles = parseClassValue('tackles');

  return {
    goals: toInt(goals),
    assists: toInt(assists),
    shots: toInt(shots),
    keyPasses: toInt(keyPasses),
    dribbles: toInt(dribbles),
    tackles: toInt(tackles),
    rating: toFloat(rating),
    minutosJugados: toInt(mins),
    tarjetasAmarillas: toInt(yel),
    tarjetasRojas: toInt(red)
  };
};

/**
 * Scrapes WhoScored player profile page using Puppeteer with retry policy
 */
const scrapePlayerStats = async (whoscoredId, abortSignal = null) => {
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
              } catch (e) { }
            });
          }

          const url = `https://www.whoscored.com/Players/${whoscoredId}/Show`;
          let response;
          try {
            response = await page.goto(url, {
              waitUntil: 'domcontentloaded',
              timeout: config.SCRAPE_TIMEOUT_MS
            });
          } catch (err) {
            if (err.name === 'TimeoutError') {
              throw new ScrapeTimeoutError();
            }
            throw err;
          }

          const status = response ? response.status() : 200;
          if (status === 404) {
            throw new PlayerNotFoundError();
          }
          if (status === 403 || status === 429) {
            throw new ScrapeBlockedError();
          }

          const html = await page.content();

          // Check if page displays not found or blocked patterns
          if (html.includes('Page Not Found') || html.includes('Player not found')) {
            throw new PlayerNotFoundError();
          }
          if (html.includes('Access Denied') || html.includes('cf-browser-verification') || html.includes('Attention Required! | Cloudflare')) {
            throw new ScrapeBlockedError();
          }

          return parsePlayerStatsFromHtml(html);
        } catch (err) {
          if (err instanceof PlayerNotFoundError || err instanceof ScrapeBlockedError) {
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
            } catch (e) { }
          }
        }
      },
      config.MAX_RETRIES,
      1000,
      (err) => !(err instanceof PlayerNotFoundError) && !(err instanceof ScrapeBlockedError) && err.message !== 'ABORTED'
    );
  });
};

module.exports = {
  parsePlayerStatsFromHtml,
  scrapePlayerStats
};
