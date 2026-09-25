const { formatErrorResponse } = require('../utils/errors');

const errorHandler = (err, req, res, next) => {
  const status = err.status || 500;
  const formatted = formatErrorResponse(err);

  if (process.env.NODE_ENV !== 'test' && status >= 500) {
    console.error(`[Error Handler] ${req.method} ${req.originalUrl}:`, err);
  }

  res.status(status).json(formatted);
};

module.exports = errorHandler;
