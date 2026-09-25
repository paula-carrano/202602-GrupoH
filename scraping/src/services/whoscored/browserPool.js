const puppeteer = require('puppeteer');
const Bottleneck = require('bottleneck');
const config = require('../../config/env');

class BrowserPool {
  constructor() {
    this.browser = null;
    // Limitador de concurrencia usando Bottleneck exclusivamente
    this.limiter = new Bottleneck({
      maxConcurrent: config.MAX_CONCURRENT_SCRAPES || 2
    });
  }

  async getBrowser() {
    if (!this.browser || !this.browser.connected) {
      this.browser = await puppeteer.launch({
        headless: 'new',
        args: [
          '--no-sandbox',
          '--disable-setuid-sandbox',
          '--disable-dev-shm-usage',
          '--disable-gpu',
          '--no-first-run',
          '--no-zygote',
          '--single-process'
        ]
      });
    }
    return this.browser;
  }

  async acquirePage() {
    const browser = await this.getBrowser();
    const page = await browser.newPage();
    await page.setUserAgent(
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
    );
    return page;
  }

  async closeBrowser() {
    if (this.browser) {
      await this.browser.close();
      this.browser = null;
    }
  }

  schedule(fn) {
    return this.limiter.schedule(fn);
  }
}

const poolInstance = new BrowserPool();

module.exports = poolInstance;
