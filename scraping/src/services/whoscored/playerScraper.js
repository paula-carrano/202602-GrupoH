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
  // Regex parsing for standalone testability without requiring a full live Chromium browser instance
  const getCell = (colName) => {
    // Top summary table matching
    const headerRegex = new RegExp(`<th>\\s*${colName}\\s*</th>`, 'i');
    const headerMatch = html.match(headerRegex);
    if (!headerMatch) return null;

    const tableMatch = html.substring(headerMatch.index);
    const tbodyMatch = tableMatch.match(/<tbody>[\s\S]*?<tr>([\s\S]*?)<\/tr>/i);
    if (!tbodyMatch) return null;

    // Find column index
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

  // Parse specialized statistics tables
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
              } catch (e) {}
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
            } catch (e) {}
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
