const browserPool = require('./browserPool');
const config = require('../../config/env');
const { withRetry } = require('../../utils/retry');
const { ScrapeBlockedError, ScrapeTimeoutError } = require('../../utils/errors');

const closePage = async (page) => {
  try {
    if (page && !page.isClosed()) await page.close();
  } catch {
    // Closing a disconnected page must not hide the scraping result.
  }
};

/** Runs a scraper with a fresh page per attempt and shared cleanup. */
const scrapeWithPage = (scrape, abortSignal, NotFoundError) => {
  const isTerminal = error =>
    error instanceof NotFoundError || error instanceof ScrapeBlockedError;

  return browserPool.schedule(() => withRetry(async () => {
    let page;
    const onAbort = () => closePage(page);
    try {
      page = await browserPool.acquirePage();
      if (abortSignal?.aborted) throw new Error('ABORTED');
      abortSignal?.addEventListener('abort', onAbort);
      return await scrape(page);
    } catch (error) {
      if (!isTerminal(error) && error.message?.includes('timeout')) {
        throw new ScrapeTimeoutError();
      }
      throw error;
    } finally {
      abortSignal?.removeEventListener('abort', onAbort);
      await closePage(page);
    }
  }, config.MAX_RETRIES, 1000,
  error => !isTerminal(error) && error.message !== 'ABORTED'));
};

module.exports = { scrapeWithPage };
