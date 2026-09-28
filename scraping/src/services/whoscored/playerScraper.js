const JSON5 = require('json5');

const { scrapeWithPage } = require('./scrapeWithPage');
const config = require('../../config/env');

const { toInt, toFloat } = require('../../utils/normalizer');

const {
  PlayerNotFoundError,
  ScrapeTimeoutError,
  ScrapeBlockedError
} = require('../../utils/errors');

const EMPTY_STATS = {
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

/**
 * Parses raw HTML string extracted from WhoScored
 * and returns a normalized PlayerStats object.
 */
const parsePlayerStatsFromHtml = (html) => {
  if (!html || typeof html !== 'string') {
    return { ...EMPTY_STATS };
  }

  const embeddedStats = parseEmbeddedStats(html);

  if (embeddedStats) {
    return embeddedStats;
  }

  return parseLegacyStats(html);
};

/**
 * Parses statistics from WhoScored embedded DataStore.
 */
const parseEmbeddedStats = (html) => {
  const scriptRegex =
    /require\.config\.params\['args'\]\s*=\s*(\{[\s\S]*?\});/;

  const scriptMatch = scriptRegex.exec(html);

  if (!scriptMatch) {
    return null;
  }

  try {
    const jsonStr = scriptMatch[1];
    const argsData = JSON5.parse(jsonStr);

    if (
      !argsData ||
      !Array.isArray(argsData.tournaments) ||
      argsData.tournaments.length === 0
    ) {
      return null;
    }

    return calculateTournamentStats(
      argsData.tournaments,
      html
    );
  } catch {
    // Intentionally ignored: invalid embedded data falls back to legacy HTML parsing.
    return null;
  }
};

/**
 * Calculates totals from tournament statistics.
 */
const calculateTournamentStats = (tournaments, html) => {
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

  for (const tournament of tournaments) {
    totalGoals += toInt(tournament.Goals);
    totalAssists += toInt(tournament.Assists);
    totalShots += toInt(tournament.TotalShots);
    totalKeyPasses += toInt(tournament.KeyPasses);
    totalDribbles += toInt(tournament.Dribbles);
    totalTackles += toInt(tournament.TotalTackles);

    totalYellow +=
      toInt(tournament.Yellow) +
      toInt(tournament.SecondYellow);

    totalRed += toInt(tournament.Red);

    const apps =
      toInt(tournament.GameStarted) +
      toInt(tournament.SubOn);

    totalApps += apps;

    const rating = toFloat(tournament.Rating);
    weightedRatingSum += rating * apps;
  }

  const avgRating =
    totalApps > 0
      ? toFloat(weightedRatingSum / totalApps)
      : 0.0;

  const totalMins = parseMinutes(html);

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
};

/**
 * Parses total minutes from match statistics.
 */
const parseMinutes = (html) => {
  const minRegex =
    /class="[^"]*col-data-mins[^"]*"[^>]*>\s*(\d+)'?\s*<\/div>/gi;

  let totalMins = 0;
  let minMatch;

  while ((minMatch = minRegex.exec(html)) !== null) {
    totalMins += toInt(minMatch[1]);
  }

  return totalMins;
};

/**
 * Extracts a cell value from the legacy statistics table.
 */
const getCell = (html, colName) => {
  const headerRegex = new RegExp(
  String.raw`<th>\s*${colName}\s*</th>`,
  'i'
);

  const headerMatch = headerRegex.exec(html);

  if (!headerMatch) {
    return null;
  }

  const tableHtml = html.substring(headerMatch.index);

  const tbodyRegex =
    /<tbody>[\s\S]*?<tr>([\s\S]*?)<\/tr>/i;

  const tbodyMatch = tbodyRegex.exec(tableHtml);

  if (!tbodyMatch) {
    return null;
  }

  const previousHtml = html.substring(
    0,
    headerMatch.index
  );

  const headerCountRegex = /<th>/gi;
  const previousHeaders =
    previousHtml.match(headerCountRegex) || [];

  const columnIndex = previousHeaders.length;

  const tdRegex = /<td>([\s\S]*?)<\/td>/gi;
  const cells = [];

  let cellMatch;

  while ((cellMatch = tdRegex.exec(tbodyMatch[1])) !== null) {
    cells.push(cellMatch[1]);
  }

  if (!cells[columnIndex]) {
    return null;
  }

  return cells[columnIndex].trim();
};

/**
 * Extracts a value using a CSS class from legacy HTML.
 */
const parseClassValue = (html, className) => {
  const regex = new RegExp(
    `class="${className}">([^<]*)<`,
    'i'
  );

  const match = regex.exec(html);

  return match ? match[1].trim() : null;
};

/**
 * Parses statistics from the legacy/server-rendered HTML table.
 */
const parseLegacyStats = (html) => {
  const mins = getCell(html, 'Mins');
  const goals = getCell(html, 'Goals');
  const assists = getCell(html, 'Assists');
  const yellow = getCell(html, 'Yel');
  const red = getCell(html, 'Red');
  const rating = getCell(html, 'Rating');

  const shots = parseClassValue(html, 'shots');
  const keyPasses = parseClassValue(html, 'key-passes');
  const dribbles = parseClassValue(html, 'dribbles');
  const tackles = parseClassValue(html, 'tackles');

  return {
    goals: toInt(goals),
    assists: toInt(assists),
    shots: toInt(shots),
    keyPasses: toInt(keyPasses),
    dribbles: toInt(dribbles),
    tackles: toInt(tackles),
    rating: toFloat(rating),
    minutosJugados: toInt(mins),
    tarjetasAmarillas: toInt(yellow),
    tarjetasRojas: toInt(red)
  };
};

/**
 * Checks whether the response indicates a blocked request.
 */
const validateResponse = (response, html) => {
  const status = response?.status() ?? 200;

  if (status === 404) {
    throw new PlayerNotFoundError();
  }

  if (status === 403 || status === 429) {
    throw new ScrapeBlockedError();
  }

  if (
    html.includes('Page Not Found') ||
    html.includes('Player not found')
  ) {
    throw new PlayerNotFoundError();
  }

  if (
    html.includes('Access Denied') ||
    html.includes('cf-browser-verification') ||
    html.includes('Attention Required! | Cloudflare')
  ) {
    throw new ScrapeBlockedError();
  }
};

/**
 * Navigates to a WhoScored player profile and extracts its HTML.
 */
const fetchPlayerHtml = async (page, whoscoredId) => {
  const url =
    `https://www.whoscored.com/Players/${whoscoredId}/Show`;

  let response;

  try {
    response = await page.goto(url, {
      waitUntil: 'domcontentloaded',
      timeout: config.SCRAPE_TIMEOUT_MS
    });
  } catch (error) {
    if (error.name === 'TimeoutError') {
      throw new ScrapeTimeoutError();
    }

    throw error;
  }

  const html = await page.content();

  validateResponse(response, html);

  return html;
};

/**
 * Scrapes WhoScored player profile page using Puppeteer
 * with retry policy.
 */
const scrapePlayerStats = (whoscoredId, abortSignal = null) =>
  scrapeWithPage(async page => {
    const html = await fetchPlayerHtml(page, whoscoredId);
    return parsePlayerStatsFromHtml(html);
  }, abortSignal, PlayerNotFoundError);

module.exports = {
  parsePlayerStatsFromHtml,
  scrapePlayerStats
};