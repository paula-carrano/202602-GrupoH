const request = require('supertest');
const app = require('../../src/index');
const config = require('../../src/config/env');

describe('Health and Auth Integration Tests', () => {
  it('GET /health should return 200 UP without requiring X-API-Key', async () => {
    const res = await request(app).get('/health');
    expect(res.status).toBe(200);
    expect(res.body).toHaveProperty('status', 'UP');
    expect(res.body).toHaveProperty('timestamp');
    expect(new Date(res.body.timestamp).toString()).not.toBe('Invalid Date');
  });

  it('GET /protected-route should reject with 401 when X-API-Key is missing', async () => {
    const res = await request(app).get('/any-protected-endpoint');
    expect(res.status).toBe(401);
    expect(res.body).toEqual({
      error: 'UNAUTHORIZED',
      message: 'Cabecera X-API-Key no proporcionada o inválida.',
      status: 401,
      timestamp: expect.any(String)
    });
  });

  it('GET /protected-route should reject with 401 when X-API-Key is invalid', async () => {
    const res = await request(app)
      .get('/any-protected-endpoint')
      .set('X-API-Key', 'invalid-key');
    expect(res.status).toBe(401);
    expect(res.body.error).toBe('UNAUTHORIZED');
  });
});
