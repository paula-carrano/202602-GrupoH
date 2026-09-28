jest.mock('../../src/services/whoscored/browserPool', () => ({
  schedule: jest.fn(fn => fn()), acquirePage: jest.fn()
}));
const pool = require('../../src/services/whoscored/browserPool');
const { scrapePlayerStats } = require('../../src/services/whoscored/playerScraper');
const { PlayerNotFoundError, ScrapeBlockedError, ScrapeTimeoutError } = require('../../src/utils/errors');
const config = require('../../src/config/env');

let page;
beforeEach(() => {
  jest.clearAllMocks();
  page = { goto: jest.fn().mockResolvedValue({ status: () => 200 }), content: jest.fn().mockResolvedValue(''), isClosed: jest.fn().mockReturnValue(false), close: jest.fn().mockResolvedValue() };
  pool.acquirePage.mockReset().mockResolvedValue(page);
});

test('accepts navigation without a response and closes the page', async () => {
  page.goto.mockResolvedValue(null);
  await expect(scrapePlayerStats(7)).resolves.toMatchObject({ goals: 0 });
  expect(page.goto).toHaveBeenCalledWith('https://www.whoscored.com/Players/7/Show', expect.objectContaining({ waitUntil: 'domcontentloaded' }));
  expect(page.close).toHaveBeenCalledTimes(1);
});

test.each([[404, '', PlayerNotFoundError], [403, '', ScrapeBlockedError], [429, '', ScrapeBlockedError], [200, 'Page Not Found', PlayerNotFoundError], [200, 'Player not found', PlayerNotFoundError], [200, 'Access Denied', ScrapeBlockedError], [200, 'cf-browser-verification', ScrapeBlockedError], [200, 'Attention Required! | Cloudflare', ScrapeBlockedError]])('rejects non-retryable response %s %s', async (status, html, errorType) => {
  page.goto.mockResolvedValue({ status: () => status });
  page.content.mockResolvedValue(html);
  await expect(scrapePlayerStats(7)).rejects.toBeInstanceOf(errorType);
  expect(pool.acquirePage).toHaveBeenCalledTimes(1);
  expect(page.close).toHaveBeenCalledTimes(1);
});

test.each([Object.assign(new Error('navigation failed'), { name: 'TimeoutError' }), new Error('connection timeout')])('maps timeouts and exhausts retries', async error => {
  page.goto.mockRejectedValue(error);
  await expect(scrapePlayerStats(7)).rejects.toBeInstanceOf(ScrapeTimeoutError);
  expect(pool.acquirePage).toHaveBeenCalledTimes(config.MAX_RETRIES + 1);
});

test('retries a transient failure and returns the successful result', async () => {
  page.goto.mockRejectedValueOnce(new Error('network failure'));
  await expect(scrapePlayerStats(7)).resolves.toMatchObject({ goals: 0 });
  expect(pool.acquirePage).toHaveBeenCalledTimes(2);
});

test('does not navigate or retry an already aborted request', async () => {
  const controller = new AbortController();
  controller.abort();
  await expect(scrapePlayerStats(7, controller.signal)).rejects.toThrow('ABORTED');
  expect(page.goto).not.toHaveBeenCalled();
  expect(pool.acquirePage).toHaveBeenCalledTimes(1);
});

test.each([false, true])('cancels in-flight work even if page close fails: %s', async closeFails => {
  let listener;
  const signal = { aborted: false, removeEventListener: jest.fn(), addEventListener: jest.fn((_, fn) => { listener = fn }) };
  if (closeFails) page.close.mockRejectedValue(new Error('already closed'));
  page.goto.mockImplementation(async () => { await listener(); return null; });
  await expect(scrapePlayerStats(7, signal)).resolves.toMatchObject({ goals: 0 });
  expect(page.close).toHaveBeenCalled();
  expect(signal.removeEventListener).toHaveBeenCalledWith('abort', listener);
});

test('does not close an already closed page', async () => {
  page.isClosed.mockReturnValue(true);
  await scrapePlayerStats(7);
  expect(page.close).not.toHaveBeenCalled();
});

test('propagates acquisition failures after retries', async () => {
  const error = new Error('browser unavailable');
  pool.acquirePage.mockRejectedValue(error);
  await expect(scrapePlayerStats(7)).rejects.toBe(error);
  expect(page.close).not.toHaveBeenCalled();
});
