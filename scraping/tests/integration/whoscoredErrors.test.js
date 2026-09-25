const request = require('supertest');
const app = require('../../src/index');
const config = require('../../src/config/env');
const playerScraper = require('../../src/services/whoscored/playerScraper');
const lineupScraper = require('../../src/services/whoscored/lineupScraper');
const {
  PlayerNotFoundError,
  MatchNotFoundError,
  ScrapeTimeoutError,
  ScrapeBlockedError
} = require('../../src/utils/errors');

describe('WhoScored Error Scenarios Integration Tests', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  describe('Player Stats Errors', () => {
    it('should return HTTP 404 with PLAYER_NOT_FOUND when player does not exist', async () => {
      jest.spyOn(playerScraper, 'scrapePlayerStats').mockRejectedValue(new PlayerNotFoundError());

      const res = await request(app)
        .get('/players/99999999/stats')
        .set('X-API-Key', config.API_KEY);

      expect(res.status).toBe(404);
      expect(res.body).toEqual({
        error: 'PLAYER_NOT_FOUND',
        message: 'No se encontró el jugador especificado en la fuente externa.',
        status: 404,
        timestamp: expect.any(String)
      });
    });

    it('should return HTTP 504 with SCRAPE_TIMEOUT when scraping times out', async () => {
      jest.spyOn(playerScraper, 'scrapePlayerStats').mockRejectedValue(new ScrapeTimeoutError());

      const res = await request(app)
        .get('/players/29400/stats')
        .set('X-API-Key', config.API_KEY);

      expect(res.status).toBe(504);
      expect(res.body.error).toBe('SCRAPE_TIMEOUT');
    });

    it('should return HTTP 502 with SCRAPE_BLOCKED when scraping is blocked', async () => {
      jest.spyOn(playerScraper, 'scrapePlayerStats').mockRejectedValue(new ScrapeBlockedError());

      const res = await request(app)
        .get('/players/29400/stats')
        .set('X-API-Key', config.API_KEY);

      expect(res.status).toBe(502);
      expect(res.body.error).toBe('SCRAPE_BLOCKED');
    });
  });

  describe('Lineup Errors', () => {
    it('should return HTTP 404 with MATCH_NOT_FOUND when match is not found on date', async () => {
      jest.spyOn(lineupScraper, 'scrapeLineup').mockRejectedValue(new MatchNotFoundError());

      const res = await request(app)
        .get('/lineups?homeTeam=NonExistentA&awayTeam=NonExistentB&date=2026-09-20')
        .set('X-API-Key', config.API_KEY);

      expect(res.status).toBe(404);
      expect(res.body.error).toBe('MATCH_NOT_FOUND');
    });
  });
});
