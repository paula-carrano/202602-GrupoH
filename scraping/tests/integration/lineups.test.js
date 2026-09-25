const request = require('supertest');
const app = require('../../src/index');
const config = require('../../src/config/env');
const lineupScraper = require('../../src/services/whoscored/lineupScraper');

describe('GET /lineups Integration Tests', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('should return 200 with MatchLineup payload including source: WHOSCORED', async () => {
    const mockLineup = {
      source: 'WHOSCORED',
      date: '2026-09-20',
      homeTeam: {
        name: 'Arsenal',
        formation: '4-3-3',
        startingXI: [{ id: 1, name: 'Raya', shirtNumber: 22, position: 'GK' }],
        bench: [{ id: 12, name: 'Neto', shirtNumber: 32, position: 'Sub' }]
      },
      awayTeam: {
        name: 'Chelsea',
        formation: '4-2-3-1',
        startingXI: [{ id: 21, name: 'Sanchez', shirtNumber: 1, position: 'GK' }],
        bench: [{ id: 32, name: 'Jorgensen', shirtNumber: 12, position: 'Sub' }]
      }
    };

    jest.spyOn(lineupScraper, 'scrapeLineup').mockResolvedValue(mockLineup);

    const res = await request(app)
      .get('/lineups?homeTeam=Arsenal&awayTeam=Chelsea&date=2026-09-20')
      .set('X-API-Key', config.API_KEY);

    expect(res.status).toBe(200);
    expect(res.body.source).toBe('WHOSCORED');
    expect(res.body.homeTeam.name).toBe('Arsenal');
    expect(res.body.homeTeam.startingXI.length).toBe(1);
  });

  it('should return 400 INVALID_REQUEST_PARAMS if date or teams are missing', async () => {
    const res = await request(app)
      .get('/lineups?homeTeam=Arsenal')
      .set('X-API-Key', config.API_KEY);

    expect(res.status).toBe(400);
    expect(res.body.error).toBe('INVALID_REQUEST_PARAMS');
  });
});
