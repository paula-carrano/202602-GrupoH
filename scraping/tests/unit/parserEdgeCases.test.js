const { parsePlayerStatsFromHtml } = require('../../src/services/whoscored/playerScraper');
const { parseLineupFromHtml } = require('../../src/services/whoscored/lineupScraper');
const { findMatchUrlFromSearchHtml, findMatchUrlFromFixturesHtml, teamsMatch, normalizeTeamName } = require('../../src/services/whoscored/teamMatcher');
const { toInt, toFloat } = require('../../src/utils/normalizer');

describe('incomplete and alternative player statistics', () => {
  test.each([null, undefined, '', 12])('returns empty stats for %s', html => {
    expect(Object.values(parsePlayerStatsFromHtml(html))).toEqual(Array(10).fill(0));
  });
  test.each(['{broken}', '{}', '{tournaments: {}}', '{tournaments: []}'])('falls back to legacy HTML for %s', data => {
    const html = `require.config.params['args'] = ${data}; <th>Goals</th><tbody><tr><td>5</td></tr></tbody>`;
    expect(parsePlayerStatsFromHtml(html).goals).toBe(5);
  });
  test('handles missing rows and cells', () => {
    expect(parsePlayerStatsFromHtml('<th>Goals</th>').goals).toBe(0);
    expect(parsePlayerStatsFromHtml('<th>Mins</th><th>Goals</th><tbody><tr><td>90</td></tr></tbody>')).toMatchObject({ goals: 0, minutosJugados: 90 });
  });
  test('aggregates tournaments with weighted ratings and minutes', () => {
    const tournaments = [
      { Goals: 2, Assists: 1, GameStarted: 2, Rating: 8, Yellow: 1, SecondYellow: 1 },
      { Goals: 3, Assists: 2, SubOn: 1, Rating: 5, Red: 1 }
    ];
    const html = `require.config.params['args'] = ${JSON.stringify({ tournaments })}; <div class="col-data-mins">90'</div><div class="col-data-mins">30</div>`;
    expect(parsePlayerStatsFromHtml(html)).toEqual({ goals: 5, assists: 3, shots: 0, keyPasses: 0, dribbles: 0, tackles: 0, rating: 7, minutosJugados: 120, tarjetasAmarillas: 2, tarjetasRojas: 1 });
  });
  test('uses zero rating when no appearances exist', () => {
    expect(parsePlayerStatsFromHtml("require.config.params['args'] = {tournaments: [{}]};").rating).toBe(0);
  });
  test.each([[null, 0, 0], [undefined, 0, 0], ['', 0, 0], ['-', 0, 0], ['N/A', 0, 0], ['invalid', 0, 0], [' 1,234.567 ', 1234, 1234.57]])('normalizes %s', (value, integer, decimal) => {
    expect(toInt(value)).toBe(integer);
    expect(toFloat(value)).toBe(decimal);
  });
});

describe('incomplete and alternative lineups', () => {
  test.each([null, undefined, '', 42])('returns an empty lineup for %s', html => {
    expect(parseLineupFromHtml(html)).toEqual({ source: 'WHOSCORED', date: '', homeTeam: { name: '', formation: 'Unknown', startingXI: [], bench: [] }, awayTeam: { name: '', formation: 'Unknown', startingXI: [], bench: [] } });
  });
  test.each(['{broken}', '{}'])('falls back from invalid embedded data %s', data => {
    const result = parseLineupFromHtml(`matchCentreData = ${data}; <div class="home"><span class="team-name">Arsenal</span><div class="away"> </body>`, '2026-01-01');
    expect(result.homeTeam.name).toBe('Arsenal');
    expect(result.homeTeam.formation).toBe('Unknown');
    expect(result.awayTeam.startingXI).toEqual([]);
  });
  test('supports alternate fields, missing teams and fallback positions', () => {
    const data = { startTime: '2026-01-02T12:00:00', home: { formation: '4-4-2', players: [
      { id: 1, knownName: 'Keeper', shirtNumber: '12', field: 'GK', isFirstEleven: true },
      { playerId: 2, jerseyNumber: '9', isFirstEleven: true },
      {}
    ] } };
    const result = parseLineupFromHtml(`matchCentreData = ${JSON.stringify(data)};`);
    expect(result.date).toBe('2026-01-02');
    expect(result.homeTeam.formation).toBe('4-4-2');
    expect(result.homeTeam.startingXI).toEqual([{ id: '1', name: 'Keeper', shirtNumber: 12, position: 'GK' }, { id: '2', name: '', shirtNumber: 9, position: 'Starter' }]);
    expect(result.homeTeam.bench).toEqual([{ id: '', name: '', shirtNumber: 0, position: 'Sub' }]);
    expect(result.awayTeam.name).toBe('');
  });
  test('handles missing formation names and non-array players', () => {
    const result = parseLineupFromHtml('matchCentreData = {away: {formations: [{}], players: {}}};');
    expect(result.date).toBe('');
    expect(result.awayTeam.formation).toBe('Unknown');
    expect(result.awayTeam.bench).toEqual([]);
  });
  test('parses an unterminated legacy section with an unknown shirt number', () => {
    const result = parseLineupFromHtml('<div class="away"><div class="starting-lineup"><span data-player-id="1" data-player-name="A" data-player-number="?" data-position="GK"></span></body>');
    expect(result.awayTeam.startingXI).toEqual([{ id: '1', name: 'A', shirtNumber: 0, position: 'GK' }]);
  });
});

describe('match discovery fallbacks', () => {
  test.each([findMatchUrlFromSearchHtml, findMatchUrlFromFixturesHtml])('handles invalid input and unmatched URLs', find => {
    for (const html of [null, '', 12, '<a href="/Matches/1/Live/Liverpool-Everton">x</a>']) {
      expect(find(html, 'Arsenal', 'Chelsea')).toBeNull();
    }
  });
  test.each(['<tr>', '<div class="search-item">'])('finds team names in %s text when slug is opaque', tag => {
    const close = tag === '<tr>' ? '</tr>' : '</div>';
    const html = `${tag}<a href="/Matches/1/Live/opaque"><b>Arsenal</b> Chelsea</a>${close}`;
    expect(findMatchUrlFromSearchHtml(html, 'Arsenal', 'Chelsea')).toBe('/Matches/1/Live/opaque');
  });
  test('skips malformed and unrelated rows before a valid match', () => {
    const html = '<tr>no link</tr><tr><a href="unfinished</tr><tr><a href="/other">Liverpool Everton</a></tr><tr><a href="/match">Arsenal Chelsea</a></tr>';
    expect(findMatchUrlFromSearchHtml(html, 'Arsenal', 'Chelsea')).toBe('/match');
  });
  test('ignores incomplete HTML blocks', () => {
    expect(findMatchUrlFromSearchHtml('<tr>unfinished', 'Arsenal', 'Chelsea')).toBeNull();
    expect(findMatchUrlFromFixturesHtml('<div class="match-item">unfinished', 'Arsenal', 'Chelsea')).toBeNull();
  });
  test('handles empty names and matching longer variants', () => {
    expect(normalizeTeamName(42)).toBe('');
    expect(teamsMatch('', 'Arsenal')).toBe(false);
    expect(teamsMatch('Arsenal', 'Arsenal London')).toBe(true);
  });
});
