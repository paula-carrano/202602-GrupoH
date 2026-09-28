jest.mock('../../src/services/whoscored/browserPool', () => ({ schedule: jest.fn(fn => fn()), acquirePage: jest.fn() }));
const pool = require('../../src/services/whoscored/browserPool');
const { scrapeLineup } = require('../../src/services/whoscored/lineupScraper');
const { MatchNotFoundError, ScrapeBlockedError, ScrapeTimeoutError } = require('../../src/utils/errors');
const config = require('../../src/config/env');
const link = '<a href="/Matches/1/Live/Arsenal-Chelsea">Arsenal Chelsea</a>';
const lineup = 'matchCentreData = {home: {name: "Arsenal"}, away: {name: "Chelsea"}};';
const run = signal => scrapeLineup('Arsenal', 'Chelsea', '2026-01-01', signal);
let page;
beforeEach(() => {
  jest.clearAllMocks();
  page = { goto: jest.fn().mockResolvedValue({ status: () => 200 }), content: jest.fn().mockResolvedValueOnce(link).mockResolvedValue(lineup), isClosed: jest.fn().mockReturnValue(false), close: jest.fn().mockResolvedValue() };
  pool.acquirePage.mockReset().mockResolvedValue(page);
});

test('discovers a match and parses its lineup', async () => {
  await expect(run()).resolves.toMatchObject({ date: '2026-01-01', homeTeam: { name: 'Arsenal' }, awayTeam: { name: 'Chelsea' } });
  expect(page.goto).toHaveBeenNthCalledWith(1, 'https://www.whoscored.com/Search/?q=Arsenal%20Chelsea', expect.any(Object));
  expect(page.goto).toHaveBeenNthCalledWith(2, 'https://www.whoscored.com/Matches/1/Live/Arsenal-Chelsea', expect.any(Object));
  expect(page.close).toHaveBeenCalledTimes(1);
});

test('falls back to fixtures after search navigation fails', async () => {
  page.goto.mockRejectedValueOnce(new Error('search unavailable'));
  await expect(run()).resolves.toMatchObject({ homeTeam: { name: 'Arsenal' } });
  expect(page.goto).toHaveBeenNthCalledWith(2, 'https://www.whoscored.com/Matches?date=2026-01-01', expect.any(Object));
});

test('accepts an absolute URL found in a search result row', async () => {
  page.content.mockReset().mockResolvedValueOnce('<tr><a href="https://www.whoscored.com/Matches/1">Arsenal Chelsea</a></tr>').mockResolvedValue(lineup);
  page.goto.mockResolvedValue(null);
  await run();
  expect(page.goto).toHaveBeenLastCalledWith('https://www.whoscored.com/Matches/1', expect.any(Object));
});

test('does not retry when no match exists', async () => {
  page.content.mockReset().mockResolvedValue('No matches');
  await expect(run()).rejects.toBeInstanceOf(MatchNotFoundError);
  expect(pool.acquirePage).toHaveBeenCalledTimes(1);
});

test('skips fixtures when date is missing', async () => {
  page.content.mockReset().mockResolvedValue('No matches');
  await expect(scrapeLineup('Arsenal', 'Chelsea')).rejects.toBeInstanceOf(MatchNotFoundError);
  expect(page.goto).toHaveBeenCalledTimes(1);
});

test('reports not found when fixture navigation also fails', async () => {
  page.goto.mockRejectedValue(new Error('offline'));
  await expect(run()).rejects.toBeInstanceOf(MatchNotFoundError);
  expect(page.goto).toHaveBeenCalledTimes(2);
});

test.each([['search', 403, ''], ['search', 429, ''], ['search', 200, 'Attention Required! | Cloudflare'], ['search', 200, 'cf-browser-verification'], ['fixtures', 403, ''], ['match', 429, '']])('stops without retry when %s is blocked (%s %s)', async (stage, status, html) => {
  page.goto.mockReset();
  page.content.mockReset();
  if (stage !== 'search') {
    page.goto.mockResolvedValueOnce({ status: () => 200 });
    page.content.mockResolvedValueOnce(stage === 'fixtures' ? '' : link);
  }
  page.goto.mockResolvedValue({ status: () => status });
  page.content.mockResolvedValue(html);
  await expect(run()).rejects.toBeInstanceOf(ScrapeBlockedError);
  expect(pool.acquirePage).toHaveBeenCalledTimes(1);
  expect(page.close).toHaveBeenCalledTimes(1);
});

test.each([Object.assign(new Error('navigation failed'), { name: 'TimeoutError' }), new Error('connection timeout'), new Error('network failure')])('retries failed match navigation: %s', async error => {
  page.content.mockReset().mockResolvedValue(link);
  page.goto.mockImplementation(async url => {
    if (url.includes('/Live/')) throw error;
    return null;
  });
  if (error.name === 'TimeoutError' || error.message.includes('timeout')) {
    await expect(run()).rejects.toBeInstanceOf(ScrapeTimeoutError);
  } else {
    await expect(run()).rejects.toBe(error);
  }
  expect(pool.acquirePage).toHaveBeenCalledTimes(config.MAX_RETRIES + 1);
});

test('does not navigate an already aborted request', async () => {
  const controller = new AbortController();
  controller.abort();
  await expect(run(controller.signal)).rejects.toThrow('ABORTED');
  expect(page.goto).not.toHaveBeenCalled();
  expect(pool.acquirePage).toHaveBeenCalledTimes(1);
});

test.each([false, true])('closes on abort and tolerates a failed close: %s', async closeFails => {
  let listener;
  const signal = { aborted: false, removeEventListener: jest.fn(), addEventListener: jest.fn((_, fn) => { listener = fn }) };
  if (closeFails) page.close.mockRejectedValue(new Error('already closed'));
  page.goto.mockImplementation(async () => { await listener(); return null; });
  await expect(run(signal)).resolves.toMatchObject({ homeTeam: { name: 'Arsenal' } });
  expect(page.close).toHaveBeenCalled();
  expect(signal.removeEventListener).toHaveBeenCalledWith('abort', listener);
});

test('does not close an already closed page', async () => {
  page.isClosed.mockReturnValue(true);
  await run();
  expect(page.close).not.toHaveBeenCalled();
});

test('propagates browser acquisition failure', async () => {
  const error = new Error('browser unavailable');
  pool.acquirePage.mockRejectedValue(error);
  await expect(run()).rejects.toBe(error);
  expect(page.close).not.toHaveBeenCalled();
});
