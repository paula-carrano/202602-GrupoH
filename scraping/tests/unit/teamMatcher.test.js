const fs = require('fs');
const path = require('path');
const {
  normalizeTeamName,
  teamsMatch,
  findMatchUrlFromFixturesHtml
} = require('../../src/services/whoscored/teamMatcher');

describe('teamMatcher unit tests', () => {
  it('should normalize team names removing FC, CF, punctuation and accents', () => {
    expect(normalizeTeamName('Arsenal FC')).toBe('arsenal');
    expect(normalizeTeamName('Atlético de Madrid')).toBe('atletico madrid');
    expect(normalizeTeamName('Chelsea F.C.')).toBe('chelsea');
  });

  it('should correctly match team variants', () => {
    expect(teamsMatch('Arsenal FC', 'Arsenal')).toBe(true);
    expect(teamsMatch('Chelsea', 'Chelsea FC')).toBe(true);
    expect(teamsMatch('Liverpool', 'Everton')).toBe(false);
  });

  it('should find match URL in match-fixtures-date.html fixture', () => {
    const htmlPath = path.resolve(__dirname, '../fixtures/whoscored/match-fixtures-date.html');
    const html = fs.readFileSync(htmlPath, 'utf8');

    const url = findMatchUrlFromFixturesHtml(html, 'Arsenal', 'Chelsea');
    expect(url).toBe('/Matches/1982341/Live/England-Premier-League-2026-2027-Arsenal-Chelsea');
  });

  it('should return null when match is not found in fixture', () => {
    const htmlPath = path.resolve(__dirname, '../fixtures/whoscored/match-fixtures-date.html');
    const html = fs.readFileSync(htmlPath, 'utf8');

    const url = findMatchUrlFromFixturesHtml(html, 'Real Madrid', 'Barcelona');
    expect(url).toBeNull();
  });
});
