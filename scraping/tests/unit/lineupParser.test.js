const fs = require('fs');
const path = require('path');
const { parseLineupFromHtml } = require('../../src/services/whoscored/lineupScraper');

describe('lineupScraper unit parsing', () => {
  it('should parse legacy match-lineup.html accurately into MatchLineup schema', () => {
    const htmlPath = path.resolve(__dirname, '../fixtures/whoscored/match-lineup.html');
    const html = fs.readFileSync(htmlPath, 'utf8');

    const lineup = parseLineupFromHtml(html, '2026-09-20');

    expect(lineup.source).toBe('WHOSCORED');
    expect(lineup.date).toBe('2026-09-20');
    expect(lineup.homeTeam.name).toBe('Arsenal');
    expect(lineup.homeTeam.formation).toBe('4-3-3');
    expect(lineup.homeTeam.startingXI.length).toBe(11);
    expect(lineup.homeTeam.startingXI[0]).toEqual({
      id: '1',
      name: 'Raya',
      shirtNumber: 22,
      position: 'GK'
    });
    expect(lineup.homeTeam.bench.length).toBe(2);

    expect(lineup.awayTeam.name).toBe('Chelsea');
    expect(lineup.awayTeam.formation).toBe('4-2-3-1');
    expect(lineup.awayTeam.startingXI.length).toBe(11);
    expect(lineup.awayTeam.bench.length).toBe(2);
  });

  it('should parse embedded matchCentreData in live Match Centre HTML', () => {
    const liveHtml = `
      <!DOCTYPE html>
      <html>
      <head><title>Bayern Munich vs Borussia Dortmund</title></head>
      <body>
        <script type="text/javascript">
          var matchCentreData = {
            matchId: 1984057,
            startTime: "2026-08-22T19:30:00",
            home: {
              teamId: 44,
              name: "Borussia Dortmund",
              formations: [{ formationName: "4-2-3-1" }],
              players: [
                { playerId: 101, name: "Kobel", shirtNo: 1, position: "GK", isFirstEleven: true },
                { playerId: 102, name: "Guirassy", shirtNo: 9, position: "FW", isFirstEleven: true },
                { playerId: 103, name: "Meyer", shirtNo: 33, position: "Sub", isFirstEleven: false }
              ]
            },
            away: {
              teamId: 37,
              name: "Bayern Munich",
              formations: [{ formationName: "4-3-3" }],
              players: [
                { playerId: 201, name: "Neuer", shirtNo: 1, position: "GK", isFirstEleven: true },
                { playerId: 83532, name: "Kane", shirtNo: 9, position: "FW", isFirstEleven: true },
                { playerId: 202, name: "Ulreich", shirtNo: 26, position: "Sub", isFirstEleven: false }
              ]
            }
          };
        </script>
      </body>
      </html>
    `;

    const lineup = parseLineupFromHtml(liveHtml, '2026-08-22');

    expect(lineup.source).toBe('WHOSCORED');
    expect(lineup.date).toBe('2026-08-22');

    expect(lineup.homeTeam.name).toBe('Borussia Dortmund');
    expect(lineup.homeTeam.formation).toBe('4-2-3-1');
    expect(lineup.homeTeam.startingXI).toEqual([
      { id: '101', name: 'Kobel', shirtNumber: 1, position: 'GK' },
      { id: '102', name: 'Guirassy', shirtNumber: 9, position: 'FW' }
    ]);
    expect(lineup.homeTeam.bench).toEqual([
      { id: '103', name: 'Meyer', shirtNumber: 33, position: 'Sub' }
    ]);

    expect(lineup.awayTeam.name).toBe('Bayern Munich');
    expect(lineup.awayTeam.formation).toBe('4-3-3');
    expect(lineup.awayTeam.startingXI).toEqual([
      { id: '201', name: 'Neuer', shirtNumber: 1, position: 'GK' },
      { id: '83532', name: 'Kane', shirtNumber: 9, position: 'FW' }
    ]);
    expect(lineup.awayTeam.bench).toEqual([
      { id: '202', name: 'Ulreich', shirtNumber: 26, position: 'Sub' }
    ]);
  });
});
