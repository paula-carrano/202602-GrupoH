/**
 * Normalizes team name by trimming, lowercasing, removing accents and stripping common football suffixes.
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
  return normA === normB || normA.includes(normB) || normB.includes(normA);
};

/**
 * Finds matching match URL from WhoScored daily fixture page HTML or Live Scores page
 */
const findMatchUrlFromFixturesHtml = (html, homeTeam, awayTeam) => {
  if (!html || typeof html !== 'string') return null;

  // 1. Check legacy mock format: <div class="match-item">
  const itemRegex = /<div class="match-item">([\s\S]*?)<\/div>/gi;
  let itemMatch;

  while ((itemMatch = itemRegex.exec(html)) !== null) {
    const block = itemMatch[1];
    const linkMatch = block.match(/href="([^"]+)"/i);
    const homeMatch = block.match(/class="home-team">([^<]+)<\/span>/i);
    const awayMatch = block.match(/class="away-team">([^<]+)<\/span>/i);

    if (linkMatch && homeMatch && awayMatch) {
      const scrapedHome = homeMatch[1].trim();
      const scrapedAway = awayMatch[1].trim();

      if (teamsMatch(scrapedHome, homeTeam) && teamsMatch(scrapedAway, awayTeam)) {
        return linkMatch[1];
      }
    }
  }

  // 2. Check live match-link / divtable-row format:
  // e.g. <a class="horiz-match-link" href="/Matches/12345/Live/..."> or generic /Matches/\d+/Live/ links
  const matchLinkRegex = /href="(\/(?:[Mm]atches|\w+\/Matches)\/(\d+)\/(?:Live|Show)\/([^"]+))"/gi;
  let mLink;
  while ((mLink = matchLinkRegex.exec(html)) !== null) {
    const url = mLink[1];
    const slug = mLink[3].toLowerCase();
    const normHome = normalizeTeamName(homeTeam).replace(/\s+/g, '-');
    const normAway = normalizeTeamName(awayTeam).replace(/\s+/g, '-');

    if (slug.includes(normHome) && slug.includes(normAway)) {
      return url;
    }
  }

  return null;
};

/**
 * Finds matching match URL from WhoScored Search results HTML:
 * https://www.whoscored.com/Search/?q=...
 */
const findMatchUrlFromSearchHtml = (html, homeTeam, awayTeam) => {
  if (!html || typeof html !== 'string') return null;

  // Search results contain links to matches in format:
  // <a href="/Matches/1982341/Live/England-Premier-League-2026-2027-Arsenal-Chelsea">...</a>
  const matchRegex = /href="(\/(?:[Mm]atches|\w+\/Matches)\/(\d+)\/(?:Live|Show)\/([^"]+))"/gi;
  let match;
  const normHome = normalizeTeamName(homeTeam);
  const normAway = normalizeTeamName(awayTeam);

  while ((match = matchRegex.exec(html)) !== null) {
    const url = match[1];
    const slug = match[3].toLowerCase().replace(/-/g, ' ');
    if (teamsMatch(slug, normHome) && teamsMatch(slug, normAway)) {
      return url;
    }
  }

  // Also check table rows in search results table (e.g. search-result / search-item)
  const searchRowRegex = /<tr>([\s\S]*?)<\/tr>|<div class="search-item">([\s\S]*?)<\/div>/gi;
  let rowMatch;
  while ((rowMatch = searchRowRegex.exec(html)) !== null) {
    const block = rowMatch[1] || rowMatch[2];
    const linkMatch = block.match(/href="([^"]*\/Matches\/\d+\/[^"]+)"/i);
    if (linkMatch) {
      const text = block.replace(/<[^>]*>/g, ' ');
      if (teamsMatch(text, normHome) && teamsMatch(text, normAway)) {
        return linkMatch[1];
      }
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
