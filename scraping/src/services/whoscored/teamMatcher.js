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
 * Finds matching match URL from WhoScored daily fixture page HTML
 */
const findMatchUrlFromFixturesHtml = (html, homeTeam, awayTeam) => {
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

  return null;
};

module.exports = {
  normalizeTeamName,
  teamsMatch,
  findMatchUrlFromFixturesHtml
};
