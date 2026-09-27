const browserPool = require('./browserPool');
const config = require('../../config/env');
const { withRetry } = require('../../utils/retry');
const { ScrapeTimeoutError, ScrapeBlockedError } = require('../../utils/errors');

const decodeHtml = value => value
  .replace(/&amp;/gi, '&')
  .replace(/&quot;/gi, '"')
  .replace(/&#39;|&apos;/gi, "'")
  .replace(/&lt;/gi, '<')
  .replace(/&gt;/gi, '>')
  .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Number(code)));

const stripTags = value => decodeHtml(value.replace(/<[^>]*>/g, ' ')).replace(/\s+/g, ' ').trim();

const parsePlayerCandidatesFromHtml = html => {
  if (typeof html !== 'string' || !html) return [];
  const candidates = new Map();
  // WhoScored has used several casing/route variants for player links. Search
  // results can also include query strings or omit the trailing slug.
  const anchorRegex = /<a\b([^>]*?)href=["']([^"']*\/players\/(\d+)(?:\/(?:show|history|matchstatistics))?(?:\/[^"']*)?(?:\?[^"']*)?)["']([^>]*)>([\s\S]*?)<\/a>/gi;
  let match;
  while ((match = anchorRegex.exec(html)) !== null) {
    const id = Number(match[3]);
    if (!Number.isSafeInteger(id) || id <= 0 || candidates.has(id)) continue;
    const href = decodeHtml(match[2]);
    const slug = href.match(/\/players\/\d+(?:\/(?:show|history|matchstatistics))?\/([^?#]+)/i)?.[1];
    const slugName = slug?.replace(/[-_]+/g, ' ');
    const linkText = stripTags(match[5]);
    const label = match[1] + match[4];
    const ariaLabel = label.match(/aria-label=["']([^"']+)["']/i)?.[1];
    const name = stripTags(ariaLabel || linkText || slugName || '');
    if (!name) continue;
    candidates.set(id, {
      whoscoredId: id,
      name,
      profileUrl: href.startsWith('http') ? href : `https://www.whoscored.com${href.startsWith('/') ? href : `/${href}`}`
    });
  }
  return [...candidates.values()];
};

const searchPlayers = async (query, abortSignal = null) => browserPool.schedule(() => withRetry(async () => {
  let page;
  try {
    page = await browserPool.acquirePage();
    if (abortSignal?.aborted) throw new Error('ABORTED');
    if (abortSignal) abortSignal.addEventListener('abort', async () => {
      try { if (page && !page.isClosed()) await page.close(); } catch (_) {}
    }, { once: true });

    let response;
    try {
      // WhoScored's current search page reads `t`; `q` renders an empty state
      // saying "Please enter your search text" while still responding with 200.
      response = await page.goto(`https://www.whoscored.com/search/?t=${encodeURIComponent(query)}`, {
        waitUntil: 'domcontentloaded', timeout: config.SCRAPE_TIMEOUT_MS
      });
    } catch (error) {
      if (error.name === 'TimeoutError') throw new ScrapeTimeoutError();
      throw error;
    }
    if (response && [403, 429].includes(response.status())) throw new ScrapeBlockedError();
    await page.waitForFunction(
      () => [...document.querySelectorAll('a[href]')].some(anchor => /\/players\/\d+\/(show|history|matchstatistics)/i.test(anchor.getAttribute('href') || '')),
      { timeout: Math.min(config.SCRAPE_TIMEOUT_MS, 5000) }
    ).catch(() => {});
    const html = await page.content();
    if (html.includes('Access Denied') || html.includes('cf-browser-verification') || html.includes('Attention Required! | Cloudflare')) {
      throw new ScrapeBlockedError();
    }
    return parsePlayerCandidatesFromHtml(html);
  } catch (error) {
    if (error instanceof ScrapeBlockedError) throw error;
    if (error.message?.toLowerCase().includes('timeout')) throw new ScrapeTimeoutError();
    throw error;
  } finally {
    if (page && !page.isClosed()) {
      try { await page.close(); } catch (_) {}
    }
  }
}, config.MAX_RETRIES, 1000, error => !(error instanceof ScrapeBlockedError) && error.message !== 'ABORTED'));

const normalizeTeam = value => (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  .toLowerCase().replace(/\b(fc|cf|afc|sc|ac)\b/g, '').replace(/[^a-z0-9]+/g, ' ').trim();

const searchTeamPlayers = async (teamName, country, abortSignal = null) => browserPool.schedule(() => withRetry(async () => {
  let page;
  try {
    page = await browserPool.acquirePage();
    if (abortSignal?.aborted) throw new Error('ABORTED');
    if (abortSignal) abortSignal.addEventListener('abort', async () => {
      try { if (page && !page.isClosed()) await page.close(); } catch (_) {}
    }, { once: true });

    const searchName = teamName.replace(/\b(fc|cf|afc|sc|ac)\b/gi, ' ').replace(/\s+/g, ' ').trim();
    const searchResponse = await page.goto(`https://www.whoscored.com/search/?t=${encodeURIComponent(searchName)}`, {
      waitUntil: 'domcontentloaded', timeout: config.SCRAPE_TIMEOUT_MS
    }).catch(error => {
      if (error.name === 'TimeoutError') throw new ScrapeTimeoutError();
      throw error;
    });
    if (searchResponse && [403, 429].includes(searchResponse.status())) throw new ScrapeBlockedError();
    await page.waitForFunction(
      () => [...document.querySelectorAll('a[href]')].some(a => /\/teams\/\d+\/show\//i.test(a.getAttribute('href') || '')),
      { timeout: Math.min(config.SCRAPE_TIMEOUT_MS, 5000) }
    ).catch(() => {});

    const expectedTeam = normalizeTeam(teamName);
    const expectedCountry = normalizeTeam(country);
    const teams = await page.evaluate(() => [...document.querySelectorAll('a[href]')]
      .filter(a => /\/teams\/\d+\/show\//i.test(a.getAttribute('href') || ''))
      .map(a => {
        const row = a.closest('tr') || a.parentElement?.parentElement;
        return { href: a.getAttribute('href'), name: a.innerText.trim(), rowText: row?.innerText || '' };
      }));
    const team = teams.find(item => {
      const hrefSlug = item.href.split('/').filter(Boolean).at(-1).split('-').slice(1).join(' ');
      const text = normalizeTeam(item.rowText);
      return normalizeTeam(item.name) === expectedTeam
        && (!expectedCountry || text.includes(expectedCountry))
        || normalizeTeam(hrefSlug) === expectedTeam && (!expectedCountry || text.includes(expectedCountry));
    });
    if (!team) return [];

    const teamUrl = team.href.startsWith('http') ? team.href : `https://www.whoscored.com${team.href}`;
    const teamResponse = await page.goto(teamUrl, { waitUntil: 'domcontentloaded', timeout: config.SCRAPE_TIMEOUT_MS })
      .catch(error => {
        if (error.name === 'TimeoutError') throw new ScrapeTimeoutError();
        throw error;
      });
    if (teamResponse && [403, 429].includes(teamResponse.status())) throw new ScrapeBlockedError();
    await page.waitForFunction(
      () => [...document.querySelectorAll('a[href]')].some(a => /\/players\/\d+\//i.test(a.getAttribute('href') || '')),
      { timeout: Math.min(config.SCRAPE_TIMEOUT_MS, 5000) }
    ).catch(() => {});
    const links = await page.evaluate(() => [...document.querySelectorAll('a[href]')]
      .filter(a => /\/players\/\d+\//i.test(a.getAttribute('href') || ''))
      .map(a => ({ href: a.getAttribute('href'), text: a.innerText.trim() })));
    const byId = new Map();
    for (const link of links) {
      const match = link.href.match(/\/players\/(\d+)\//i);
      if (!match) continue;
      const whoscoredId = Number(match[1]);
      if (!Number.isSafeInteger(whoscoredId) || whoscoredId <= 0 || byId.has(whoscoredId)) continue;
      const slug = link.href.match(/\/players\/\d+\/show\/([^?#]+)/i)?.[1];
      const name = (slug ? decodeURIComponent(slug).replace(/[-_]+/g, ' ') : link.text).trim();
      if (!name) continue;
      byId.set(whoscoredId, {
        whoscoredId, name,
        profileUrl: link.href.startsWith('http') ? link.href : `https://www.whoscored.com${link.href}`
      });
    }
    return [...byId.values()];
  } catch (error) {
    if (error instanceof ScrapeBlockedError) throw error;
    if (error.message?.toLowerCase().includes('timeout')) throw new ScrapeTimeoutError();
    throw error;
  } finally {
    if (page && !page.isClosed()) {
      try { await page.close(); } catch (_) {}
    }
  }
}, config.MAX_RETRIES, 1000, error => !(error instanceof ScrapeBlockedError) && error.message !== 'ABORTED'));

module.exports = { parsePlayerCandidatesFromHtml, searchPlayers, searchTeamPlayers };
