jest.mock('../../src/services/whoscored/playerSearchScraper', () => ({ searchPlayers: jest.fn(), searchTeamPlayers: jest.fn() }));
const { EventEmitter } = require('node:events');
const controller = require('../../src/controllers/playerController');
const scraper = require('../../src/services/whoscored/playerSearchScraper');

describe.each([
  ['searchPlayers', { q: ' Alex Doe ' }, ['Alex Doe']],
  ['searchTeamPlayers', { name: ' Team FC ', country: ' England ' }, ['Team FC', 'England']]
])('%s controller', (method, query, args) => {
  let req; let res; let next;
  beforeEach(() => {
    jest.resetAllMocks();
    req = Object.assign(new EventEmitter(), { query });
    res = { writableEnded: false, status: jest.fn().mockReturnThis(), json: jest.fn() };
    next = jest.fn();
  });
  test('passes trimmed input and cancellation signal, returns provider results', async () => {
    scraper[method].mockResolvedValue([{ whoscoredId: 1 }]);
    await controller[method](req, res, next);
    expect(scraper[method]).toHaveBeenCalledWith(...args, expect.any(AbortSignal));
    expect(res.status).toHaveBeenCalledWith(200);
    expect(res.json).toHaveBeenCalledWith([{ whoscoredId: 1 }]);
    res.writableEnded = true; req.emit('close');
    expect(scraper[method].mock.calls[0].at(-1).aborted).toBe(false);
  });
  test('forwards provider failures', async () => {
    const error = new Error('provider failed'); scraper[method].mockRejectedValue(error);
    await controller[method](req, res, next);
    expect(next).toHaveBeenCalledWith(error);
  });
  test('aborts disconnected clients without sending another response', async () => {
    scraper[method].mockImplementation(async (...callArgs) => {
      req.emit('close');
      expect(callArgs.at(-1).aborted).toBe(true);
      throw new Error('ABORTED');
    });
    await controller[method](req, res, next);
    expect(next).not.toHaveBeenCalled(); expect(res.json).not.toHaveBeenCalled();
  });
});

test.each([
  ['searchPlayers', {}], ['searchPlayers', { q: ['ab'] }], ['searchPlayers', { q: 'a' }],
  ['searchPlayers', { q: 'a'.repeat(121) }], ['searchTeamPlayers', {}],
  ['searchTeamPlayers', { name: ['ab'] }], ['searchTeamPlayers', { name: 'a' }],
  ['searchTeamPlayers', { name: 'a'.repeat(121) }],
  ['searchTeamPlayers', { name: 'ab', country: 'a'.repeat(81) }]
])('%s rejects invalid query %j', async (method, query) => {
  const next = jest.fn();
  await controller[method]({ query }, {}, next);
  expect(next).toHaveBeenCalledWith(expect.objectContaining({ name: 'InvalidRequestParamsError' }));
});

test('team search accepts an omitted country', async () => {
  scraper.searchTeamPlayers.mockResolvedValue([]);
  const req = Object.assign(new EventEmitter(), { query: { name: 'Team' } });
  const res = { status: jest.fn().mockReturnThis(), json: jest.fn() };
  await controller.searchTeamPlayers(req, res, jest.fn());
  expect(scraper.searchTeamPlayers).toHaveBeenLastCalledWith('Team', '', expect.any(AbortSignal));
});
