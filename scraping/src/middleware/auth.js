const config = require('../config/env');
const { UnauthorizedError } = require('../utils/errors');

const authMiddleware = (req, res, next) => {
  const apiKey = req.header('X-API-Key');

  if (!apiKey || apiKey !== config.API_KEY) {
    return next(new UnauthorizedError('Cabecera X-API-Key no proporcionada o inválida.'));
  }

  next();
};

module.exports = authMiddleware;
