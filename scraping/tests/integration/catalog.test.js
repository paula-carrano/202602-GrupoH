const request = require('supertest');
const nock = require('nock');
const app = require('../../src/index');
const config = require('../../src/config/env');

describe('Catalog endpoints', () => {
  afterEach(() => {
    const done = nock.isDone();
    nock.cleanAll();
    expect(done).toBe(true);
  });
  const api = () => nock('https://api.football-data.org/v4', {
    reqheaders: { 'X-Auth-Token': config.FOOTBALL_DATA_API_KEY }
  });
  const get = path => request(app).get(path).set('X-API-Key', config.API_KEY);

  it('imports teams for the current competition', async () => {
    api().get('/competitions/PL/teams').reply(200, { teams: [{ id: 57, name: 'Arsenal', crest: 'extra' }] });
    const res = await get('/competitions/pl/teams');
    expect(res.status).toBe(200);
    expect(res.body).toEqual([{ id: 57, name: 'Arsenal' }]);
  });
  it('normalizes squad and preserves missing fields', async () => {
    api().get('/teams/57').reply(200, { id: 57, name: 'Arsenal',
      squad: [{ id: 1, name: 'Full Name', dateOfBirth: '2000-01-01', position: 'Midfield' }] });
    const res = await get('/teams/57/squad');
    expect(res.status).toBe(200);
    expect(res.body.team.id).toBe(57);
    expect(res.body.players[0]).toEqual({ id: 1, name: 'Full Name', firstName: null,
      lastName: null, birthDate: '2000-01-01', position: 'Midfield', nationality: null });
  });
  it('accepts an explicit empty squad', async () => {
    api().get('/teams/57').reply(200, { id: 57, name: 'Arsenal', squad: [] });
    expect((await get('/teams/57/squad')).body.players).toEqual([]);
  });
  it.each([{}, { id: 57, name: 'Arsenal' }, { id: 58, name: 'Arsenal', squad: [] }])(
    'rejects incomplete or mismatched squads', async body => {
      api().get('/teams/57').reply(200, body);
      const res = await get('/teams/57/squad');
      expect(res.status).toBe(502);
      expect(res.body.error).toBe('INVALID_EXTERNAL_RESPONSE');
    });
  it.each([[401,502,'EXTERNAL_API_AUTH_ERROR'],[403,502,'EXTERNAL_API_AUTH_ERROR'],
    [429,429,'RATE_LIMIT_EXCEEDED'],[404,404,'MATCH_NOT_FOUND']])(
    'propagates provider failure %s', async (remote,status,code) => {
      api().get('/teams/57').reply(remote, {});
      const res = await get('/teams/57/squad');
      expect(res.status).toBe(status); expect(res.body.error).toBe(code);
    });
  it('validates input and protects both endpoints', async () => {
    expect((await get('/competitions/INVALID/teams')).status).toBe(400);
    for (const id of ['0','-1','1abc','1.5']) expect((await get('/teams/'+id+'/squad')).status).toBe(400);
    expect((await request(app).get('/teams/57/squad')).status).toBe(401);
    expect((await request(app).get('/competitions/PL/teams')).status).toBe(401);
  });
});
