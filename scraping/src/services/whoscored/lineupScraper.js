const JSON5 = require('json5');

const browserPool = require('./browserPool');
const teamMatcher = require('./teamMatcher');
const config = require('../../config/env');
const { withRetry } = require('../../utils/retry');

const {
  MatchNotFoundError,
  ScrapeTimeoutError,
  ScrapeBlockedError
} = require('../../utils/errors');

const EMPTY_TEAM = {
  name: '',
  formation: 'Unknown',
  startingXI: [],
  bench: []
};

/**
 * Creates an empty lineup response.
 */
const createEmptyLineup = (matchDate = '') => ({
  source: 'WHOSCORED',
  date: matchDate,
  homeTeam: { ...EMPTY_TEAM },
  awayTeam: { ...EMPTY_TEAM }
});

/**
 * Parses a player from WhoScored match centre data.
 */
const parseMatchCentrePlayer = (player) => {
  let id = '';

  if (player.playerId !== undefined) {
    id = String(player.playerId);
  } else if (player.id !== undefined) {
    id = String(player.id);
  }

  const shirtNumber = Number.parseInt(
    player.shirtNo || player.shirtNumber || player.jerseyNumber || 0,
    10
  );

  let position = player.position || player.field;

  if (!position) {
    position = player.isFirstEleven ? 'Starter' : 'Sub';
  }

  return {
    id,
    name: player.name || player.knownName || '',
    shirtNumber,
    position
  };
};

/**
 * Parses one team from WhoScored match centre data.
 */
const parseMatchCentreSide = (sideData) => {
  if (!sideData) {
    return { ...EMPTY_TEAM };
  }

  const name = sideData.name || '';

  let formation = 'Unknown';

  if (sideData.formations?.length > 0) {
    formation = sideData.formations[0].formationName || 'Unknown';
  } else if (sideData.formation) {
    formation = sideData.formation;
  }

  const startingXI = [];
  const bench = [];

  if (Array.isArray(sideData.players)) {
    for (const player of sideData.players) {
      const entry = parseMatchCentrePlayer(player);

      if (player.isFirstEleven) {
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

/**
 * Parses embedded matchCentreData from WhoScored HTML.
 */
const parseMatchCentreData = (html, matchDate) => {
  const matchCentreRegex =
    /(?:var\s+matchCentreData|matchCentreData)\s*=\s*(\{[\s\S]*?\});/;

  const matchCentreMatch = matchCentreRegex.exec(html);

  if (!matchCentreMatch) {
    return null;
  }

  try {
    const jsonStr = matchCentreMatch[1];
    const data = JSON5.parse(jsonStr);

    if (!data || (!data.home && !data.away)) {
      return null;
    }

    return {
      source: 'WHOSCORED',
      date: matchDate || data.startTime?.substring(0, 10) || '',
      homeTeam: parseMatchCentreSide(data.home),
      awayTeam: parseMatchCentreSide(data.away)
    };
  } catch (error) {
  // Invalid matchCentreData is expected to fall back to legacy HTML parsing.
    return null;
  }
};

/**
 * Returns the HTML chunk corresponding to one team.
 */
const getTeamChunk = (html, teamClass) => {
  let regex;

  if (teamClass === 'home') {
    regex = /<div class="home">([\s\S]*?)<div class="away">/i;
  } else {
    regex = /<div class="away">([\s\S]*?)<\/body>/i;
  }

  const match = regex.exec(html);

  return match ? match[1] : '';
};

/**
 * Parses players from one section of the legacy HTML.
 */
const parseLegacyPlayerSection = (chunk, sectionName) => {
  const startTag = `<div class="${sectionName}">`;
  const startIndex = chunk.indexOf(startTag);

  if (startIndex === -1) {
    return [];
  }

  const afterStart = chunk.substring(startIndex + startTag.length);

  const nextSectionRegex =
    /<div class="substitutes">|<\/div>\s*<\/div>/i;

  const nextSectionMatch = nextSectionRegex.exec(afterStart);

  const sectionHtml = nextSectionMatch
    ? afterStart.substring(0, nextSectionMatch.index)
    : afterStart;

  const playerRegex =
    /data-player-id="([^"]+)"\s+data-player-name="([^"]+)"\s+data-player-number="([^"]+)"\s+data-position="([^"]+)"/gi;

  const players = [];
  let playerMatch;

  while ((playerMatch = playerRegex.exec(sectionHtml)) !== null) {
    players.push({
      id: playerMatch[1],
      name: playerMatch[2],
      shirtNumber: Number.parseInt(playerMatch[3], 10) || 0,
      position: playerMatch[4]
    });
  }

  return players;
};

/**
 * Parses one team using the legacy DOM-like HTML structure.
 */
const parseLegacyTeamBlock = (html, teamClass) => {
  const chunk = getTeamChunk(html, teamClass);

  if (!chunk) {
    return { ...EMPTY_TEAM };
  }

  const formationRegex = /class="formation">([^<]+)<\/span>/i;
  const nameRegex = /class="team-name">([^<]+)<\/span>/i;

  const formationMatch = formationRegex.exec(chunk);
  const nameMatch = nameRegex.exec(chunk);

  return {
    name: nameMatch ? nameMatch[1].trim() : '',
    formation: formationMatch
      ? formationMatch[1].trim()
      : 'Unknown',
    startingXI: parseLegacyPlayerSection(
      chunk,
      'starting-lineup'
    ),
    bench: parseLegacyPlayerSection(
      chunk,
      'substitutes'
    )
  };
};

/**
 * Parses match lineup HTML string and extracts lineups for both teams.
 *
 * Strategy 1:
 * Check embedded matchCentreData in script blocks.
 *
 * Strategy 2:
 * Legacy fallback using CSS selectors/DOM attributes.
 */
const parseLineupFromHtml = (html, matchDate = '') => {
  if (!html || typeof html !== 'string') {
    return createEmptyLineup(matchDate);
  }

  const matchCentreData = parseMatchCentreData(
    html,
    matchDate
  );

  if (matchCentreData) {
    return matchCentreData;
  }

  return {
    source: 'WHOSCORED',
    date: matchDate,
    homeTeam: parseLegacyTeamBlock(html, 'home'),
    awayTeam: parseLegacyTeamBlock(html, 'away')
  };
};

/**
 * Checks whether a WhoScored response indicates blocking.
 */
const ensureResponseIsNotBlocked = (response, html) => {
  if (
    response &&
    (response.status() === 403 || response.status() === 429)
  ) {
    throw new ScrapeBlockedError();
  }

  if (
    html.includes('Attention Required! | Cloudflare') ||
    html.includes('cf-browser-verification')
  ) {
    throw new ScrapeBlockedError();
  }
};

/**
 * Searches WhoScored for the requested match.
 */
const findMatchFromSearch = async (
  page,
  homeTeam,
  awayTeam
) => {
  const searchQuery = encodeURIComponent(
    `${homeTeam} ${awayTeam}`
  );

  const searchUrl =
    `https://www.whoscored.com/Search/?q=${searchQuery}`;

  try {
    const searchRes = await page.goto(searchUrl, {
      waitUntil: 'domcontentloaded',
      timeout: config.SCRAPE_TIMEOUT_MS
    });

    const searchHtml = await page.content();

    ensureResponseIsNotBlocked(searchRes, searchHtml);

    return teamMatcher.findMatchUrlFromSearchHtml(
      searchHtml,
      homeTeam,
      awayTeam
    );
  } catch (error) {
    if (error instanceof ScrapeBlockedError) {
      throw error;
    }

    return null;
  }
};

/**
 * Searches WhoScored fixtures by date.
 */
const findMatchFromFixtures = async (
  page,
  homeTeam,
  awayTeam,
  date
) => {
  if (!date) {
    return null;
  }

  const fixturesUrl =
    `https://www.whoscored.com/Matches?date=${date}`;

  try {
    const fixturesRes = await page.goto(fixturesUrl, {
      waitUntil: 'domcontentloaded',
      timeout: config.SCRAPE_TIMEOUT_MS
    });

    const fixturesHtml = await page.content();

    ensureResponseIsNotBlocked(
      fixturesRes,
      fixturesHtml
    );

    return teamMatcher.findMatchUrlFromFixturesHtml(
      fixturesHtml,
      homeTeam,
      awayTeam
    );
  } catch (error) {
    if (error instanceof ScrapeBlockedError) {
      throw error;
    }

    return null;
  }
};

/**
 * Finds the requested match using search and date fallback.
 */
const findMatchUrl = async (
  page,
  homeTeam,
  awayTeam,
  date
) => {
  let matchUrl = await findMatchFromSearch(
    page,
    homeTeam,
    awayTeam
  );

  if (!matchUrl) {
    matchUrl = await findMatchFromFixtures(
      page,
      homeTeam,
      awayTeam,
      date
    );
  }

  return matchUrl;
};

/**
 * Navigates to the match page and parses the lineup.
 */
const scrapeMatchPage = async (
  page,
  matchUrl,
  date
) => {
  const fullMatchUrl = matchUrl.startsWith('http')
    ? matchUrl
    : `https://www.whoscored.com${matchUrl}`;

  let matchRes;

  try {
    matchRes = await page.goto(fullMatchUrl, {
      waitUntil: 'domcontentloaded',
      timeout: config.SCRAPE_TIMEOUT_MS
    });
  } catch (error) {
    if (error.name === 'TimeoutError') {
      throw new ScrapeTimeoutError();
    }

    throw error;
  }

  const matchHtml = await page.content();

  ensureResponseIsNotBlocked(matchRes, matchHtml);

  return parseLineupFromHtml(matchHtml, date);
};

/**
 * Scrapes WhoScored lineup with resilient match discovery
 * and retry policy.
 */
const scrapeLineup = async (
  homeTeam,
  awayTeam,
  date,
  abortSignal = null
) => {
  return browserPool.schedule(async () => {
    return withRetry(
      async () => {
        let page = null;

        try {
          page = await browserPool.acquirePage();

          if (abortSignal?.aborted) {
            throw new Error('ABORTED');
          }

          if (abortSignal) {
            abortSignal.addEventListener(
              'abort',
              async () => {
                try {
                  if (page && !page.isClosed()) {
                    await page.close();
                  }
                } catch (error) {
                  // Page may already be closed.
                }
              }
            );
          }

          const matchUrl = await findMatchUrl(
            page,
            homeTeam,
            awayTeam,
            date
          );

          if (!matchUrl) {
            throw new MatchNotFoundError(
              `No se encontró el partido entre ${homeTeam} y ${awayTeam} para la fecha ${date} en WhoScored.`
            );
          }

          return await scrapeMatchPage(
            page,
            matchUrl,
            date
          );
        } catch (error) {
          if (
            error instanceof MatchNotFoundError ||
            error instanceof ScrapeBlockedError
          ) {
            throw error;
          }

          if (error.message?.includes('timeout')) {
            throw new ScrapeTimeoutError();
          }

          throw error;
        } finally {
          if (page && !page.isClosed()) {
            try {
              await page.close();
            } catch (error) {
              // Page may already be closed.
            }
          }
        }
      },
      config.MAX_RETRIES,
      1000,
      (error) =>
        !(error instanceof MatchNotFoundError) &&
        !(error instanceof ScrapeBlockedError) &&
        error.message !== 'ABORTED'
    );
  });
};

module.exports = {
  parseLineupFromHtml,
  scrapeLineup
};