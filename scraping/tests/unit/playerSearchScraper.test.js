jest.mock('../../src/services/whoscored/browserPool', () => ({
  schedule: jest.fn(task => task()), acquirePage: jest.fn()
}));
const pool = require('../../src/services/whoscored/browserPool');
const { parsePlayerCandidatesFromHtml: parse, searchPlayers, searchTeamPlayers } = require('../../src/services/whoscored/playerSearchScraper');
const { ScrapeBlockedError, ScrapeTimeoutError } = require('../../src/utils/errors');
const config = require('../../src/config/env');

describe('player search HTML parsing', () => {
  test.each([null, '', 12])('ignores non-HTML input %s', input => expect(parse(input)).toEqual([]));
  test('decodes entities, accepts route variants and retains the first occurrence of each ID', () => {
    const result = parse(`<a href="/Players/1/Show/Jose-Perez" aria-label="José &amp; Pérez">ignored</a>
      <a href='/players/1/history/duplicate'>duplicate</a>
      <a href='/players/2?x=1&amp;y=2'><span>John</span> Smith</a>
      <a href='/players/3/matchstatistics/Alex_Doe'></a>
      <a href='/players/4/Other-Name'></a>`);
    expect(result.map(p => [p.whoscoredId, p.name])).toEqual([
      [1, 'José & Pérez'], [2, 'John Smith'], [3, 'Alex Doe'], [4, 'Other Name']
    ]);
    expect(result[1].profileUrl).toBe('https://www.whoscored.com/players/2?x=1&y=2');
  });
  test('rejects invalid IDs, empty names and off-site or non-HTTP URLs', () => {
    const links = ['/players/0/show/A', '/players/9007199254740992/show/A', '/teams/1/show/A',
      '/players/1', '/players/2/show/%ZZ', 'https://evil.test/players/1/show/A',
      '//evil.test/players/1/show/A', 'javascript:alert(1)', 'https://user@www.whoscored.com/players/1/A', 'http://['];
    expect(parse(links.map(href => `<a href="${href}"></a>`).join(''))).toEqual([]);
  });
  test('handles large malformed markup and malformed numeric entities without throwing', () => {
    expect(() => parse('<'.repeat(100000) + '<a href="/players/1/show/A">&#99999999999999;</a>')).not.toThrow();
  });
});

describe('browser search lifecycle', () => {
  let page;
  beforeEach(() => {
    jest.clearAllMocks();
    page = {
      goto: jest.fn().mockResolvedValue({ status: () => 200 }),
      content: jest.fn().mockResolvedValue('<a href="/players/10/show/Alex-Doe">Alex Doe</a>'),
      waitForFunction: jest.fn().mockResolvedValue(true),
      evaluate: jest.fn(), isClosed: jest.fn().mockReturnValue(false),
      close: jest.fn().mockResolvedValue(undefined)
    };
    pool.acquirePage.mockReset().mockResolvedValue(page);
  });
  test('uses the t query parameter, returns players and closes the page', async () => {
    await expect(searchPlayers('Alex & Doe')).resolves.toEqual([
      { whoscoredId: 10, name: 'Alex Doe', profileUrl: 'https://www.whoscored.com/players/10/show/Alex-Doe' }
    ]);
    expect(page.goto).toHaveBeenCalledWith('https://www.whoscored.com/search/?t=Alex%20%26%20Doe', expect.any(Object));
    expect(page.close).toHaveBeenCalledTimes(1);
  });
  test.each([403, 429])('does not retry HTTP %s', async status => {
    page.goto.mockResolvedValue({ status: () => status });
    await expect(searchPlayers('Alex')).rejects.toBeInstanceOf(ScrapeBlockedError);
    expect(pool.acquirePage).toHaveBeenCalledTimes(1);
    expect(page.close).toHaveBeenCalledTimes(1);
  });
  test.each(['Access Denied', 'cf-browser-verification', 'Attention Required! | Cloudflare'])('detects block page %s', async html => {
    page.content.mockResolvedValue(html);
    await expect(searchPlayers('Alex')).rejects.toBeInstanceOf(ScrapeBlockedError);
    expect(pool.acquirePage).toHaveBeenCalledTimes(1);
  });
  test.each([Object.assign(new Error('navigation'), { name: 'TimeoutError' }), new Error('connection timeout')])(
    'normalizes timeouts and bounds retries', async error => {
      page.goto.mockRejectedValue(error);
      await expect(searchPlayers('Alex')).rejects.toBeInstanceOf(ScrapeTimeoutError);
      expect(pool.acquirePage).toHaveBeenCalledTimes(config.MAX_RETRIES + 1);
    });
  test('propagates non-timeout failures and closes pages', async () => {
    page.goto.mockRejectedValue(new Error('offline'));
    await expect(searchPlayers('Alex')).rejects.toThrow('offline');
    expect(page.close).toHaveBeenCalledTimes(config.MAX_RETRIES + 1);
  });
  test('retries browser acquisition failures without a page to close', async () => {
    pool.acquirePage.mockRejectedValueOnce(new Error('browser starting'));
    await expect(searchPlayers('Alex')).resolves.toHaveLength(1);
    expect(pool.acquirePage).toHaveBeenCalledTimes(2);
  });
  test('a missing navigation response or link wait timeout does not discard usable HTML', async () => {
    page.goto.mockResolvedValue(null);
    page.waitForFunction.mockRejectedValue(new Error('timeout'));
    page.close.mockRejectedValue(new Error('already disconnected'));
    await expect(searchPlayers('Alex')).resolves.toHaveLength(1);
  });
  test('does not acquire a page for an already cancelled operation', async () => {
    const controller = new AbortController(); controller.abort();
    await expect(searchPlayers('Alex', controller.signal)).rejects.toThrow('ABORTED');
    expect(pool.acquirePage).not.toHaveBeenCalled();
  });
  test('cancellation during acquisition closes the acquired page', async () => {
    const controller = new AbortController();
    pool.acquirePage.mockImplementation(async () => { controller.abort(); return page; });
    await expect(searchPlayers('Alex', controller.signal)).rejects.toThrow('ABORTED');
    expect(page.goto).not.toHaveBeenCalled(); expect(page.close).toHaveBeenCalledTimes(1);
  });
  test('cancellation during navigation is not retried and removes the listener', async () => {
    const controller = new AbortController();
    const remove = jest.spyOn(controller.signal, 'removeEventListener');
    page.goto.mockImplementation(async () => { controller.abort(); throw new Error('Target closed'); });
    await expect(searchPlayers('Alex', controller.signal)).rejects.toThrow('ABORTED');
    expect(pool.acquirePage).toHaveBeenCalledTimes(1);
    expect(remove).toHaveBeenCalledWith('abort', expect.any(Function));
  });
  test('completed requests remove listeners and skip closing a page already closed', async () => {
    const controller = new AbortController();
    const remove = jest.spyOn(controller.signal, 'removeEventListener');
    page.isClosed.mockReturnValue(true);
    await searchPlayers('Alex', controller.signal);
    expect(remove).toHaveBeenCalled(); expect(page.close).not.toHaveBeenCalled();
  });

  test('team search matches country and slug, deduplicates roster and prefers full slug names', async () => {
    page.evaluate.mockResolvedValueOnce([
      { href: 'https://evil.test/teams/1/show/England-Arsenal', name: 'Arsenal', rowText: 'England' },
      { href: '/teams/2/show/Other-Arsenal', name: 'Arsenal', rowText: 'Other' },
      { href: '/teams/3/show/England-Arsenal', name: 'Alternate label', rowText: 'England' }
    ]).mockResolvedValueOnce([
      { href: '/players/10/show/Alex-Doe', text: 'A. Doe' },
      { href: '/players/10/history/Alex-Doe', text: 'Duplicate' },
      { href: '/players/11/', text: 'Full Name' },
      { href: '/players/0/show/Invalid', text: 'Invalid' }
    ]);
    const result = await searchTeamPlayers('Arsenal FC', 'England');
    expect(page.goto.mock.calls.map(args => args[0])).toEqual([
      'https://www.whoscored.com/search/?t=Arsenal', 'https://www.whoscored.com/teams/3/show/England-Arsenal'
    ]);
    expect(result.map(p => p.name)).toEqual(['Alex Doe', 'Full Name']);
  });
  test('missing teams return an empty result without a second navigation', async () => {
    page.evaluate.mockResolvedValue([{ href: '/teams/1/show/England-Other', name: 'Other', rowText: '' }]);
    await expect(searchTeamPlayers('Arsenal', null)).resolves.toEqual([]);
    expect(page.goto).toHaveBeenCalledTimes(1);
  });
  test('team HTTP block stops roster processing', async () => {
    page.evaluate.mockResolvedValueOnce([{ href: '/teams/1/show/England-Arsenal', name: 'Arsenal', rowText: '' }]);
    page.goto.mockResolvedValueOnce(null).mockResolvedValueOnce({ status: () => 403 });
    await expect(searchTeamPlayers('Arsenal', null)).rejects.toBeInstanceOf(ScrapeBlockedError);
    expect(pool.acquirePage).toHaveBeenCalledTimes(1);
  });
});
