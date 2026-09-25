/**
 * Executes an async operation with exponential backoff and jitter.
 * Non-retryable errors (e.g. 404 PlayerNotFoundError or MatchNotFoundError) fail immediately.
 */
const withRetry = async (fn, maxRetries = 3, baseDelayMs = 1000, isRetryable = null) => {
  let attempt = 0;

  while (attempt <= maxRetries) {
    try {
      return await fn();
    } catch (err) {
      attempt++;

      // Check if error is retryable
      if (isRetryable && !isRetryable(err)) {
        throw err;
      }

      // If attempts exhausted, rethrow
      if (attempt > maxRetries) {
        throw err;
      }

      // Calculate exponential backoff with full jitter
      // Delay = rand(0, baseDelay * 2^(attempt - 1))
      const factor = Math.pow(2, attempt - 1);
      const maxDelay = baseDelayMs * factor;
      const delay = Math.floor(Math.random() * maxDelay);

      // In test mode, don't actually sleep to avoid slowing down the suite
      if (process.env.NODE_ENV !== 'test') {
        await new Promise((resolve) => setTimeout(resolve, delay));
      }
    }
  }
};

module.exports = {
  withRetry
};
