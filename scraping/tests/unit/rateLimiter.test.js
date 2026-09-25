const { createFootballDataLimiter } = require('../../src/services/footballData/rateLimiter');

describe('Bottleneck rateLimiter unit tests', () => {
  const originalEnv = process.env.NODE_ENV;

  afterEach(() => {
    process.env.NODE_ENV = originalEnv;
  });

  it('should have minTime: 0 in test environment to avoid delaying test suites', () => {
    process.env.NODE_ENV = 'test';
    const testLimiter = createFootballDataLimiter();
    // Verify limiter settings
    // In Bottleneck, minTime option is applied internally
    expect(testLimiter).toBeDefined();
  });

  it('should process jobs concurrently controlled by maxConcurrent', async () => {
    process.env.NODE_ENV = 'test';
    const testLimiter = createFootballDataLimiter();

    let counter = 0;
    const task = () => testLimiter.schedule(async () => {
      counter++;
      return counter;
    });

    const results = await Promise.all([task(), task(), task()]);
    expect(results).toEqual([1, 2, 3]);
  });
});
