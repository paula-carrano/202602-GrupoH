const request = require('supertest');
const app = require('../../src/index');
const config = require('../../src/config/env');
const playerScraper = require('../../src/services/whoscored/playerScraper');

describe('GET /players/:whoscoredId/stats Integration Tests', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('should return 200 with 10 quantitative fields when valid X-API-Key is provided', async () => {
    const mockStats = {
      goals: 10,
      assists: 5,
      shots: 40,
      keyPasses: 28,
      dribbles: 22,
      tackles: 12,
      rating: 7.42,
      minutosJugados: 1620,
      tarjetasAmarillas: 2,
      tarjetasRojas: 0
    };

    jest.spyOn(playerScraper, 'scrapePlayerStats').mockResolvedValue(mockStats);

    const res = await request(app)
      .get('/players/29400/stats')
      .set('X-API-Key', config.API_KEY);

    expect(res.status).toBe(200);
    expect(res.body).toEqual(mockStats);
  });

  it('should return 401 when X-API-Key is missing', async () => {
    const res = await request(app).get('/players/29400/stats');
    expect(res.status).toBe(401);
    expect(res.body.error).toBe('UNAUTHORIZED');
  });
});
