const fs = require('fs');
const path = require('path');
const request = require('supertest');
const app = require('../../src/index');
const config = require('../../src/config/env');
const mockFootballData = require('../helpers/httpMock');

describe('Matches endpoints integration tests', () => {
  afterEach(() => {
    mockFootballData.cleanAll();
  });

  describe('GET /competitions/:competitionCode/matches', () => {
    it('should return 200 with normalized fixtures on successful API response', async () => {
      const fixturePath = path.resolve(__dirname, '../fixtures/football-data/matches-pl.json');
      const fixtureData = JSON.parse(fs.readFileSync(fixturePath, 'utf8'));

      mockFootballData.getCompetitionMatches('PL', fixtureData, 200, {
        dateFrom: '2026-09-01',
        dateTo: '2026-09-30'
      });

      const res = await request(app)
        .get('/competitions/PL/matches?dateFrom=2026-09-01&dateTo=2026-09-30')
        .set('X-API-Key', config.API_KEY);

      expect(res.status).toBe(200);
      expect(Array.isArray(res.body)).toBe(true);
      expect(res.body.length).toBe(2);
      expect(res.body[0]).toHaveProperty('id', 432501);
      expect(res.body[0]).toHaveProperty('status', 'FINISHED');
      expect(res.body[0].homeTeam.name).toBe('Arsenal FC');
    });

    it('should return 400 INVALID_REQUEST_PARAMS for invalid competition code', async () => {
      const res = await request(app)
        .get('/competitions/INVALID/matches')
        .set('X-API-Key', config.API_KEY);

      expect(res.status).toBe(400);
      expect(res.body.error).toBe('INVALID_REQUEST_PARAMS');
    });

    it('should return 429 RATE_LIMIT_EXCEEDED when Football-Data.org returns 429', async () => {
      const fixturePath = path.resolve(__dirname, '../fixtures/football-data/rate-limit-429.json');
      const fixtureData = JSON.parse(fs.readFileSync(fixturePath, 'utf8'));

      mockFootballData.getCompetitionMatches('PL', fixtureData, 429);

      const res = await request(app)
        .get('/competitions/PL/matches')
        .set('X-API-Key', config.API_KEY);

      expect(res.status).toBe(429);
      expect(res.body.error).toBe('RATE_LIMIT_EXCEEDED');
    });

    it('should return 502 EXTERNAL_API_AUTH_ERROR when Football-Data.org returns 401', async () => {
      const fixturePath = path.resolve(__dirname, '../fixtures/football-data/auth-error-401.json');
      const fixtureData = JSON.parse(fs.readFileSync(fixturePath, 'utf8'));

      mockFootballData.getCompetitionMatches('PL', fixtureData, 401);

      const res = await request(app)
        .get('/competitions/PL/matches')
        .set('X-API-Key', config.API_KEY);

      expect(res.status).toBe(502);
      expect(res.body.error).toBe('EXTERNAL_API_AUTH_ERROR');
    });
  });

  describe('GET /matches/:matchId', () => {
    it('should return 200 with match detail', async () => {
      const fixturePath = path.resolve(__dirname, '../fixtures/football-data/match-detail.json');
      const fixtureData = JSON.parse(fs.readFileSync(fixturePath, 'utf8'));

      mockFootballData.getMatch(432501, fixtureData, 200);

      const res = await request(app)
        .get('/matches/432501')
        .set('X-API-Key', config.API_KEY);

      expect(res.status).toBe(200);
      expect(res.body.id).toBe(432501);
      expect(res.body.homeTeam.name).toBe('Arsenal FC');
    });

    it('should return 404 MATCH_NOT_FOUND when match does not exist in Football-Data.org', async () => {
      mockFootballData.getMatch(999999, { message: 'Not found' }, 404);

      const res = await request(app)
        .get('/matches/999999')
        .set('X-API-Key', config.API_KEY);

      expect(res.status).toBe(404);
      expect(res.body.error).toBe('MATCH_NOT_FOUND');
    });
  });
});
