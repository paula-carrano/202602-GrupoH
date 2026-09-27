const { load } = require('cheerio/slim');
const browserPool = require('./browserPool');
const config = require('../../config/env');
const { withRetry } = require('../../utils/retry');
const { ScrapeTimeoutError, ScrapeBlockedError } = require('../../utils/errors');

const ORIGIN = 'https://www.whoscored.com';
const normalizeSpace = value => value.replace(/\s+/g, ' ').trim();
const normalizeTeam = value => (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  .toLowerCase().replace(/\b(fc|cf|afc|sc|ac)\b/g, '').replace(/[^a-z0-9]+/g, ' ').trim();

// External HTML must not redirect the browser to other hosts or protocols.
const providerUrl = href => {
  try {
    const url = new URL(href, ORIGIN);
    if (url.origin !== ORIGIN || url.username || url.password) return null;
    return url;
  } catch { return null; }
};

const candidateFromLink = (href, text, preferSlug = false) => {
  const url = providerUrl(href);
  if (!url) return null;
  const match = /^\/players\/(\d+)(?:\/|$)/i.exec(url.pathname);
  if (!match) return null;
  const whoscoredId = Number(match[1]);
  if (!Number.isSafeInteger(whoscoredId) || whoscoredId <= 0) return null;
  const parts = url.pathname.split('/').filter(Boolean).slice(2);
  if (['show', 'history', 'matchstatistics'].includes(parts[0]?.toLowerCase())) parts.shift();
  let slug;
  try { slug = decodeURIComponent(parts.join(' ')).replace(/[-_]+/g, ' '); }
  catch { slug = ''; }
  const name = normalizeSpace(preferSlug ? slug || text : text || slug);
  return name ? { whoscoredId, name, profileUrl: url.href } : null;
};

const uniqueCandidates = candidates => {
  const byId = new Map();
  for (const candidate of candidates) {
    if (candidate && !byId.has(candidate.whoscoredId)) byId.set(candidate.whoscoredId, candidate);
  }
  return [...byId.values()];
};

const parsePlayerCandidatesFromHtml = html => {
  if (typeof html !== 'string' || !html) return [];
  const $ = load(html);
  return uniqueCandidates($('a[href]').toArray().map(anchor => {
    const link = $(anchor);
    return candidateFromLink(link.attr('href'), link.attr('aria-label') || link.text());
  }));
};

const closePage = async page => {
  try { if (page && !page.isClosed()) await page.close(); }
  catch { /* Closing a disconnected browser must not hide the result. */ }
};

const assertNotAborted = signal => {
  if (signal?.aborted) throw new Error('ABORTED');
};

const withSearchPage = (operation, signal) => browserPool.schedule(() => withRetry(async () => {
  let page;
  const onAbort = () => { void closePage(page); };
  try {
    assertNotAborted(signal);
    page = await browserPool.acquirePage();
    assertNotAborted(signal);
    signal?.addEventListener('abort', onAbort, { once: true });
    const result = await operation(page);
    assertNotAborted(signal);
    return result;
  } catch (error) {
    assertNotAborted(signal);
    if (error.name === 'TimeoutError' || error.message?.toLowerCase().includes('timeout')) {
      throw new ScrapeTimeoutError();
    }
    throw error;
  } finally {
    signal?.removeEventListener('abort', onAbort);
    await closePage(page);
  }
}, config.MAX_RETRIES, 1000, error => !(error instanceof ScrapeBlockedError) && error.message !== 'ABORTED'));

const navigate = async (page, url) => {
  const response = await page.goto(url, {
    waitUntil: 'domcontentloaded', timeout: config.SCRAPE_TIMEOUT_MS
  });
  if (response && [403, 429].includes(response.status())) throw new ScrapeBlockedError();
};

const waitForLinks = async (page, section) => {
  await page.waitForFunction(
    segment => [...document.querySelectorAll('a[href]')].some(anchor =>
      (anchor.getAttribute('href') || '').toLowerCase().includes(`/${segment}/`)),
    { timeout: Math.min(config.SCRAPE_TIMEOUT_MS, 5000) }, section
  ).catch(() => {});
};

const searchPlayers = (query, signal = null) => withSearchPage(async page => {
  await navigate(page, `${ORIGIN}/search/?t=${encodeURIComponent(query)}`);
  await waitForLinks(page, 'players');
  const html = await page.content();
  if (['Access Denied', 'cf-browser-verification', 'Attention Required! | Cloudflare']
    .some(marker => html.includes(marker))) throw new ScrapeBlockedError();
  return parsePlayerCandidatesFromHtml(html);
}, signal);

const matchesTeam = (item, expectedTeam, expectedCountry) => {
  const url = providerUrl(item.href);
  if (!url) return false;
  const slug = url.pathname.split('/').findLast(Boolean) || '';
  const slugName = slug.split('-').slice(1).join(' ');
  const sameName = normalizeTeam(item.name) === expectedTeam || normalizeTeam(slugName) === expectedTeam;
  return sameName && (!expectedCountry || normalizeTeam(item.rowText).includes(expectedCountry));
};

const searchTeamPlayers = (teamName, country, signal = null) => withSearchPage(async page => {
  const searchName = normalizeSpace(teamName.replace(/\b(fc|cf|afc|sc|ac)\b/gi, ' '));
  await navigate(page, `${ORIGIN}/search/?t=${encodeURIComponent(searchName)}`);
  await waitForLinks(page, 'teams');
  const teams = await page.evaluate(() => [...document.querySelectorAll('a[href]')]
    .filter(a => /\/teams\/\d+\/show\//i.test(a.getAttribute('href') || ''))
    .map(a => {
      const row = a.closest('tr') || a.parentElement?.parentElement;
      return { href: a.getAttribute('href'), name: a.innerText.trim(), rowText: row?.innerText || '' };
    }));
  const team = teams.find(item => matchesTeam(item, normalizeTeam(teamName), normalizeTeam(country)));
  if (!team) return [];
  await navigate(page, providerUrl(team.href).href);
  await waitForLinks(page, 'players');
  const links = await page.evaluate(() => [...document.querySelectorAll('a[href]')]
    .filter(a => /\/players\/\d+\//i.test(a.getAttribute('href') || ''))
    .map(a => ({ href: a.getAttribute('href'), text: a.innerText.trim() })));
  return uniqueCandidates(links.map(link => candidateFromLink(link.href, link.text, true)));
}, signal);

module.exports = { parsePlayerCandidatesFromHtml, searchPlayers, searchTeamPlayers };
