const request = require('supertest');
const app = require('../../src/index');
const config = require('../../src/config/env');
const lineupScraper = require('../../src/services/whoscored/lineupScraper');

describe('GET /lineups Integration Tests', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  describe('GET /lineups', () => {
    it('should return 200 with MatchLineup payload including source: WHOSCORED', async () => {
      const mockLineup = {
        source: 'WHOSCORED',
        date: '2026-09-20',
        homeTeam: {
          name: 'Arsenal',
          formation: '4-3-3',
          startingXI: [
            {
              id: '1',
              name: 'Raya',
              shirtNumber: 22,
              position: 'GK'
            }
          ],
          bench: [
            {
              id: '12',
              name: 'Neto',
              shirtNumber: 32,
              position: 'Sub'
            }
          ]
        },
        awayTeam: {
          name: 'Chelsea',
          formation: '4-2-3-1',
          startingXI: [
            {
              id: '21',
              name: 'Sanchez',
              shirtNumber: 1,
              position: 'GK'
            }
          ],
          bench: [
            {
              id: '32',
              name: 'Jorgensen',
              shirtNumber: 12,
              position: 'Sub'
            }
          ]
        }
      };

      jest
        .spyOn(lineupScraper, 'scrapeLineup')
        .mockResolvedValue(mockLineup);

      const res = await request(app)
        .get(
          '/lineups?homeTeam=Arsenal&awayTeam=Chelsea&date=2026-09-20'
        )
        .set('X-API-Key', config.API_KEY);

      expect(res.status).toBe(200);

      expect(res.body.source).toBe('WHOSCORED');
      expect(res.body.date).toBe('2026-09-20');

      expect(res.body.homeTeam.name).toBe('Arsenal');
      expect(res.body.homeTeam.formation).toBe('4-3-3');
      expect(res.body.homeTeam.startingXI).toHaveLength(1);
      expect(res.body.homeTeam.startingXI[0].name).toBe('Raya');

      expect(res.body.awayTeam.name).toBe('Chelsea');
      expect(res.body.awayTeam.formation).toBe('4-2-3-1');
      expect(res.body.awayTeam.startingXI).toHaveLength(1);
      expect(res.body.awayTeam.startingXI[0].name).toBe('Sanchez');
    });

    it('should return 400 INVALID_REQUEST_PARAMS if date or teams are missing', async () => {
      const res = await request(app)
        .get('/lineups?homeTeam=Arsenal')
        .set('X-API-Key', config.API_KEY);

      expect(res.status).toBe(400);
      expect(res.body.error).toBe('INVALID_REQUEST_PARAMS');
    });
  });

  describe('parseLineupFromHtml', () => {
    it('should return an empty lineup when HTML is invalid', () => {
      const result = lineupScraper.parseLineupFromHtml(
        null,
        '2026-09-20'
      );

      expect(result).toEqual({
        source: 'WHOSCORED',
        date: '2026-09-20',
        homeTeam: {
          name: '',
          formation: 'Unknown',
          startingXI: [],
          bench: []
        },
        awayTeam: {
          name: '',
          formation: 'Unknown',
          startingXI: [],
          bench: []
        }
      });
    });

    it('should parse lineup from embedded matchCentreData', () => {
      const html = `
        <script>
          var matchCentreData = {
            startTime: '2026-09-20T15:00:00',
            home: {
              name: 'Arsenal',
              formation: '4-3-3',
              players: [
                {
                  playerId: 1,
                  name: 'Raya',
                  shirtNo: 22,
                  position: 'GK',
                  isFirstEleven: true
                },
                {
                  playerId: 12,
                  name: 'Neto',
                  shirtNo: 32,
                  position: 'Sub',
                  isFirstEleven: false
                }
              ]
            },
            away: {
              name: 'Chelsea',
              formation: '4-2-3-1',
              players: [
                {
                  playerId: 21,
                  name: 'Sanchez',
                  shirtNo: 1,
                  position: 'GK',
                  isFirstEleven: true
                },
                {
                  playerId: 32,
                  name: 'Jorgensen',
                  shirtNo: 12,
                  position: 'Sub',
                  isFirstEleven: false
                }
              ]
            }
          };
        </script>
      `;

      const result = lineupScraper.parseLineupFromHtml(
        html,
        '2026-09-20'
      );

      expect(result.source).toBe('WHOSCORED');
      expect(result.date).toBe('2026-09-20');

      expect(result.homeTeam.name).toBe('Arsenal');
      expect(result.homeTeam.formation).toBe('4-3-3');

      expect(result.homeTeam.startingXI).toHaveLength(1);
      expect(result.homeTeam.startingXI[0]).toEqual({
        id: '1',
        name: 'Raya',
        shirtNumber: 22,
        position: 'GK'
      });

      expect(result.homeTeam.bench).toHaveLength(1);
      expect(result.homeTeam.bench[0]).toEqual({
        id: '12',
        name: 'Neto',
        shirtNumber: 32,
        position: 'Sub'
      });

      expect(result.awayTeam.name).toBe('Chelsea');
      expect(result.awayTeam.formation).toBe('4-2-3-1');

      expect(result.awayTeam.startingXI).toHaveLength(1);
      expect(result.awayTeam.startingXI[0]).toEqual({
        id: '21',
        name: 'Sanchez',
        shirtNumber: 1,
        position: 'GK'
      });

      expect(result.awayTeam.bench).toHaveLength(1);
      expect(result.awayTeam.bench[0]).toEqual({
        id: '32',
        name: 'Jorgensen',
        shirtNumber: 12,
        position: 'Sub'
      });
    });

    it('should use startTime as date when matchDate is not provided', () => {
      const html = `
        <script>
          var matchCentreData = {
            startTime: '2026-09-20T15:30:00',
            home: {
              name: 'Arsenal',
              formation: '4-3-3',
              players: []
            },
            away: {
              name: 'Chelsea',
              formation: '4-2-3-1',
              players: []
            }
          };
        </script>
      `;

      const result = lineupScraper.parseLineupFromHtml(html);

      expect(result.date).toBe('2026-09-20');
    });

    it('should support formation from formations array', () => {
      const html = `
        <script>
          var matchCentreData = {
            home: {
              name: 'Arsenal',
              formations: [
                {
                  formationName: '4-3-3'
                }
              ],
              players: []
            },
            away: {
              name: 'Chelsea',
              formations: [
                {
                  formationName: '4-2-3-1'
                }
              ],
              players: []
            }
          };
        </script>
      `;

      const result = lineupScraper.parseLineupFromHtml(
        html,
        '2026-09-20'
      );

      expect(result.homeTeam.formation).toBe('4-3-3');
      expect(result.awayTeam.formation).toBe('4-2-3-1');
    });

    it('should use id when playerId is not available', () => {
      const html = `
        <script>
          var matchCentreData = {
            home: {
              name: 'Arsenal',
              formation: '4-3-3',
              players: [
                {
                  id: 99,
                  name: 'Player',
                  shirtNumber: 10,
                  position: 'FW',
                  isFirstEleven: true
                }
              ]
            },
            away: {
              name: 'Chelsea',
              formation: '4-2-3-1',
              players: []
            }
          };
        </script>
      `;

      const result = lineupScraper.parseLineupFromHtml(
        html,
        '2026-09-20'
      );

      expect(result.homeTeam.startingXI[0].id).toBe('99');
    });

    it('should use knownName when player name is not available', () => {
      const html = `
        <script>
          var matchCentreData = {
            home: {
              name: 'Arsenal',
              formation: '4-3-3',
              players: [
                {
                  playerId: 99,
                  knownName: 'Player Known Name',
                  shirtNo: 10,
                  position: 'FW',
                  isFirstEleven: true
                }
              ]
            },
            away: {
              name: 'Chelsea',
              formation: '4-2-3-1',
              players: []
            }
          };
        </script>
      `;

      const result = lineupScraper.parseLineupFromHtml(
        html,
        '2026-09-20'
      );

      expect(
        result.homeTeam.startingXI[0].name
      ).toBe('Player Known Name');
    });

    it('should fallback to Starter and Sub positions when position is missing', () => {
      const html = `
        <script>
          var matchCentreData = {
            home: {
              name: 'Arsenal',
              formation: '4-3-3',
              players: [
                {
                  playerId: 1,
                  name: 'Starter Player',
                  shirtNo: 10,
                  isFirstEleven: true
                },
                {
                  playerId: 2,
                  name: 'Sub Player',
                  shirtNo: 20,
                  isFirstEleven: false
                }
              ]
            },
            away: {
              name: 'Chelsea',
              formation: '4-2-3-1',
              players: []
            }
          };
        </script>
      `;

      const result = lineupScraper.parseLineupFromHtml(
        html,
        '2026-09-20'
      );

      expect(
        result.homeTeam.startingXI[0].position
      ).toBe('Starter');

      expect(
        result.homeTeam.bench[0].position
      ).toBe('Sub');
    });

    it('should fallback to legacy HTML parsing when matchCentreData is invalid', () => {
      const html = `
        <script>
          var matchCentreData = {
            invalid:
          };
        </script>

        <div class="home">
          <span class="team-name">Arsenal</span>
          <span class="formation">4-3-3</span>

          <div class="starting-lineup">
            <div
              data-player-id="1"
              data-player-name="Raya"
              data-player-number="22"
              data-position="GK">
            </div>
          </div>

          <div class="substitutes">
          </div>
        </div>

        <div class="away">
          <span class="team-name">Chelsea</span>
          <span class="formation">4-2-3-1</span>

          <div class="starting-lineup">
            <div
              data-player-id="21"
              data-player-name="Sanchez"
              data-player-number="1"
              data-position="GK">
            </div>
          </div>

          <div class="substitutes">
          </div>
        </div>

        </body>
      `;

      const result = lineupScraper.parseLineupFromHtml(
        html,
        '2026-09-20'
      );

      expect(result.source).toBe('WHOSCORED');

      expect(result.homeTeam.name).toBe('Arsenal');
      expect(result.homeTeam.formation).toBe('4-3-3');
      expect(result.homeTeam.startingXI).toHaveLength(1);

      expect(result.homeTeam.startingXI[0]).toEqual({
        id: '1',
        name: 'Raya',
        shirtNumber: 22,
        position: 'GK'
      });

      expect(result.awayTeam.name).toBe('Chelsea');
      expect(result.awayTeam.formation).toBe('4-2-3-1');
      expect(result.awayTeam.startingXI).toHaveLength(1);
    });

    it('should parse legacy HTML when matchCentreData is not present', () => {
      const html = `
        <div class="home">
          <span class="team-name">Arsenal</span>
          <span class="formation">4-3-3</span>

          <div class="starting-lineup">
            <div
              data-player-id="1"
              data-player-name="Raya"
              data-player-number="22"
              data-position="GK">
            </div>
          </div>

          <div class="substitutes">
            <div
              data-player-id="12"
              data-player-name="Neto"
              data-player-number="32"
              data-position="Sub">
            </div>
          </div>
        </div>

        <div class="away">
          <span class="team-name">Chelsea</span>
          <span class="formation">4-2-3-1</span>

          <div class="starting-lineup">
            <div
              data-player-id="21"
              data-player-name="Sanchez"
              data-player-number="1"
              data-position="GK">
            </div>
          </div>

          <div class="substitutes">
            <div
              data-player-id="32"
              data-player-name="Jorgensen"
              data-player-number="12"
              data-position="Sub">
            </div>
          </div>
        </div>

        </body>
      `;

      const result = lineupScraper.parseLineupFromHtml(
        html,
        '2026-09-20'
      );

      expect(result.homeTeam.name).toBe('Arsenal');
      expect(result.homeTeam.formation).toBe('4-3-3');
      expect(result.homeTeam.startingXI).toHaveLength(1);
      expect(result.homeTeam.bench).toHaveLength(1);

      expect(result.awayTeam.name).toBe('Chelsea');
      expect(result.awayTeam.formation).toBe('4-2-3-1');
      expect(result.awayTeam.startingXI).toHaveLength(1);
      expect(result.awayTeam.bench).toHaveLength(1);
    });

    it('should return empty teams when team blocks are not present', () => {
      const html = `
        <html>
          <body>
            <div>Some unrelated content</div>
          </body>
        </html>
      `;

      const result = lineupScraper.parseLineupFromHtml(
        html,
        '2026-09-20'
      );

      expect(result.homeTeam).toEqual({
        name: '',
        formation: 'Unknown',
        startingXI: [],
        bench: []
      });

      expect(result.awayTeam).toEqual({
        name: '',
        formation: 'Unknown',
        startingXI: [],
        bench: []
      });
    });
  });
});

