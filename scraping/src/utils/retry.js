const crypto = require("node:crypto");

/**
 * Executes an async operation with exponential backoff and jitter.
 * Non-retryable errors (e.g. 404 PlayerNotFoundError or MatchNotFoundError) fail immediately.
 */
const withRetry = async (
	fn,
	maxRetries = 3,
	baseDelayMs = 1000,
	isRetryable = null,
) => {
	let attempt = 0;

	while (attempt <= maxRetries) {
		try {
			// Indicamos a SonarQube que la espera secuencial es intencional aquí
			// eslint-disable-next-line no-await-in-loop
			return await fn(); // NOSONAR
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
			// Delay = random(0, baseDelay * 2^(attempt - 1))
			const factor = Math.pow(2, attempt - 1);
			const maxDelay = baseDelayMs * factor;
			const delay = crypto.randomInt(maxDelay);

			// In test mode, don't actually sleep to avoid slowing down the suite
			if (process.env.NODE_ENV !== "test") {
				// Indicamos a SonarQube que la espera secuencial es intencional aquí
				// eslint-disable-next-line no-await-in-loop
				await new Promise((resolve) => setTimeout(resolve, delay)); //NOSONAR
			}
		}
	}
};

module.exports = {
	withRetry,
};
