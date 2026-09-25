const browserPool = require('../../src/services/whoscored/browserPool');

describe('Concurrency Regulation Integration Tests', () => {
  it('should regulate concurrent scraping tasks through browserPool limiter', async () => {
    let activeTasks = 0;
    let maxObservedActiveTasks = 0;

    const mockScrape = () => {
      return browserPool.schedule(async () => {
        activeTasks++;
        if (activeTasks > maxObservedActiveTasks) {
          maxObservedActiveTasks = activeTasks;
        }
        await new Promise((resolve) => setTimeout(resolve, 50));
        activeTasks--;
        return true;
      });
    };

    const tasks = [mockScrape(), mockScrape(), mockScrape(), mockScrape()];
    await Promise.all(tasks);

    // Concurrency limit is configured to 2
    expect(maxObservedActiveTasks).toBeLessThanOrEqual(2);
  });
});
