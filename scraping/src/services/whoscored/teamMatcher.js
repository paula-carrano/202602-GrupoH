/**
 * Normalizes team name by trimming, lowercasing, removing accents
 * and stripping common football suffixes.
 */
const normalizeTeamName = (name) => {
  if (!name || typeof name !== 'string') return '';

  return name
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/f\.c\.|c\.f\./g, '')
    .replace(/[^a-z0-9]/g, ' ')
    .replace(/\b(fc|cf|afc|club|de|futbol|cd|sc|ac)\b/g, '')
    .replace(/\s+/g, ' ')
    .trim();
};

/**
 * Checks whether two team names match (exact or substring).
 */
const teamsMatch = (teamA, teamB) => {
  const normA = normalizeTeamName(teamA);
  const normB = normalizeTeamName(teamB);

  if (!normA || !normB) return false;

  return (
    normA === normB ||
    normA.includes(normB) ||
    normB.includes(normA)
  );
};

/**
 * Extracts match URLs from WhoScored HTML.
 */
const extractMatchUrls = (html) => {
  const urls = [];
  const regex =
    /href="(\/(?:matches|\w+\/Matches)\/\d+\/(?:Live|Show)\/[^"]+)"/gi;

  let match;

  while ((match = regex.exec(html)) !== null) {
    urls.push(match[1]);
  }

  return urls;
};

/**
 * Checks whether a match URL contains both teams.
 */
const urlMatchesTeams = (url, homeTeam, awayTeam) => {
  const slug = url.split('/').pop().toLowerCase().replaceAll(',', '');

  const normalizedHome = normalizeTeamName(homeTeam)
    .replace(/\s+/g, '-');

  const normalizedAway = normalizeTeamName(awayTeam)
    .replace(/\s+/g, '-');

  return (
    slug.includes(normalizedHome) &&
    slug.includes(normalizedAway)
  );
};

/**
 * Extracts HTML blocks using tag and optional class name.
 */
const extractHtmlBlocks = (html, tagName, className) => {
  const blocks = [];

  const openTag = className
    ? `<${tagName} class="${className}">`
    : `<${tagName}>`;

  const endTag = `</${tagName}>`;

  let start = html.indexOf(openTag);

  while (start !== -1) {
    const contentStart = start + openTag.length;
    const end = html.indexOf(endTag, contentStart);

    if (end === -1) {
      break;
    }

    blocks.push(html.slice(contentStart, end));

    start = html.indexOf(
      openTag,
      end + endTag.length
    );
  }

  return blocks;
};

/**
 * Extracts the value of an href attribute without using a regex.
 */
const extractHref = (block) => {
  const hrefStart = block.indexOf('href="');

  if (hrefStart === -1) {
    return null;
  }

  const valueStart = hrefStart + 'href="'.length;
  const valueEnd = block.indexOf('"', valueStart);

  if (valueEnd === -1) {
    return null;
  }

  return block.slice(valueStart, valueEnd);
};

/**
 * Removes HTML tags without using a regular expression.
 */
const stripHtmlTags = (html) => {
  const result = [];
  let insideTag = false;

  for (const character of html) {
    if (character === '<') {
      insideTag = true;
      result.push(' ');
      continue;
    }

    if (character === '>') {
      insideTag = false;
      continue;
    }

    if (!insideTag) {
      result.push(character);
    }
  }

  return result.join('');
};

/**
 * Finds a match URL inside an HTML block using team names.
 */
const findMatchUrlInBlock = (
  block,
  homeTeam,
  awayTeam
) => {
  const url = extractHref(block);

  if (!url) {
    return null;
  }

  const text = stripHtmlTags(block);

  if (
    teamsMatch(text, homeTeam) &&
    teamsMatch(text, awayTeam)
  ) {
    return url;
  }

  return null;
};

/**
 * Finds matching match URL from WhoScored daily fixture page HTML
 * or Live Scores page.
 */
const findMatchUrlFromFixturesHtml = (
  html,
  homeTeam,
  awayTeam
) => {
  if (!html || typeof html !== 'string') {
    return null;
  }

  // 1. Check legacy mock format:
  // <div class="match-item">
  const matchItems = extractHtmlBlocks(
    html,
    'div',
    'match-item'
  );

  for (const block of matchItems) {
    const result = findMatchUrlInBlock(
      block,
      homeTeam,
      awayTeam
    );

    if (result) {
      return result;
    }
  }

  // 2. Check live match-link / divtable-row format.
  const matchUrls = extractMatchUrls(html);

  return (
    matchUrls.find((url) =>
      urlMatchesTeams(
        url,
        homeTeam,
        awayTeam
      )
    ) || null
  );
};

/**
 * Finds matching match URL from WhoScored Search results HTML.
 */
const findMatchUrlFromSearchHtml = (
  html,
  homeTeam,
  awayTeam
) => {
  if (!html || typeof html !== 'string') {
    return null;
  }

  // Search result match URLs.
  const matchUrls = extractMatchUrls(html);

  const directMatch = matchUrls.find((url) =>
    urlMatchesTeams(
      url,
      homeTeam,
      awayTeam
    )
  );

  if (directMatch) {
    return directMatch;
  }

  // Check table rows in search results.
  const rows = extractHtmlBlocks(
    html,
    'tr'
  );

  for (const block of rows) {
    const result = findMatchUrlInBlock(
      block,
      homeTeam,
      awayTeam
    );

    if (result) {
      return result;
    }
  }

  // Check search-result / search-item blocks.
  const searchItems = extractHtmlBlocks(
    html,
    'div',
    'search-item'
  );

  for (const block of searchItems) {
    const result = findMatchUrlInBlock(
      block,
      homeTeam,
      awayTeam
    );

    if (result) {
      return result;
    }
  }

  return null;
};

module.exports = {
  normalizeTeamName,
  teamsMatch,
  findMatchUrlFromFixturesHtml,
  findMatchUrlFromSearchHtml
};

