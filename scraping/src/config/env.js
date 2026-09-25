const dotenv = require('dotenv');
const path = require('path');

dotenv.config({ path: path.resolve(__dirname, '../../.env') });

const getEnv = (key, defaultValue = undefined) => {
  const value = process.env[key] !== undefined && process.env[key] !== '' ? process.env[key] : defaultValue;
  return value;
};

const getRequiredEnv = (key) => {
  const value = process.env[key];
  if (!value && process.env.NODE_ENV !== 'test') {
    throw new Error(`[Config Error] Missing required environment variable: ${key}`);
  }
  return value;
};

const config = {
  NODE_ENV: getEnv('NODE_ENV', 'development'),
  PORT: parseInt(getEnv('PORT', '3000'), 10),
  API_KEY: getEnv('API_KEY', process.env.NODE_ENV === 'test' ? 'test-api-key' : undefined),
  FOOTBALL_DATA_API_KEY: getEnv('FOOTBALL_DATA_API_KEY', process.env.NODE_ENV === 'test' ? 'test-football-data-key' : undefined),
  SCRAPE_TIMEOUT_MS: parseInt(getEnv('SCRAPE_TIMEOUT_MS', '15000'), 10),
  MAX_RETRIES: parseInt(getEnv('MAX_RETRIES', '3'), 10),
  MAX_CONCURRENT_SCRAPES: parseInt(getEnv('MAX_CONCURRENT_SCRAPES', '2'), 10),
};

if (process.env.NODE_ENV !== 'test') {
  if (!config.API_KEY) {
    throw new Error('[Config Error] Missing required environment variable: API_KEY');
  }
  if (!config.FOOTBALL_DATA_API_KEY) {
    console.warn('[Config Warn] FOOTBALL_DATA_API_KEY is not defined. Calls to Football-Data.org may fail.');
  }
}

module.exports = config;
