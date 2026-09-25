const Bottleneck = require('bottleneck');

/**
 * Creates a configured Bottleneck instance for Football-Data.org.
 * In test mode (NODE_ENV === 'test'), minTime is set to 0 to prevent 6-second test delays.
 * In production/dev, limits requests to 10 per minute (minTime: 6000ms).
 */
const createFootballDataLimiter = () => {
  const isTest = process.env.NODE_ENV === 'test';

  return new Bottleneck({
    reservoir: 10,
    reservoirRefreshAmount: 10,
    reservoirRefreshInterval: 60 * 1000,
    maxConcurrent: 1,
    minTime: isTest ? 0 : 6000
  });
};

const limiter = createFootballDataLimiter();

module.exports = {
  createFootballDataLimiter,
  limiter
};
