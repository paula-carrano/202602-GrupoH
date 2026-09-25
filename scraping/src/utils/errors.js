class AppError extends Error {
  constructor(message, status = 500, code = 'INTERNAL_ERROR', details = null) {
    super(message);
    this.name = this.constructor.name;
    this.status = status;
    this.code = code;
    this.details = details;
    Error.captureStackTrace(this, this.constructor);
  }
}

class UnauthorizedError extends AppError {
  constructor(message = 'Cabecera X-API-Key no proporcionada o inválida.') {
    super(message, 401, 'UNAUTHORIZED');
  }
}

class InvalidRequestParamsError extends AppError {
  constructor(message = 'Parámetros de solicitud inválidos o faltantes.', details = null) {
    super(message, 400, 'INVALID_REQUEST_PARAMS', details);
  }
}

class PlayerNotFoundError extends AppError {
  constructor(message = 'No se encontró el jugador especificado en la fuente externa.') {
    super(message, 404, 'PLAYER_NOT_FOUND');
  }
}

class MatchNotFoundError extends AppError {
  constructor(message = 'No se encontró el partido especificado.') {
    super(message, 404, 'MATCH_NOT_FOUND');
  }
}

class ScrapeTimeoutError extends AppError {
  constructor(message = 'Tiempo de espera agotado al consultar la fuente externa.') {
    super(message, 504, 'SCRAPE_TIMEOUT');
  }
}

class ScrapeBlockedError extends AppError {
  constructor(message = 'La fuente externa ha bloqueado la solicitud de scraping o detectado automatización.') {
    super(message, 502, 'SCRAPE_BLOCKED');
  }
}

class RateLimitExceededError extends AppError {
  constructor(message = 'Límite de tasa de la API externa de Football-Data.org excedido.') {
    super(message, 429, 'RATE_LIMIT_EXCEEDED');
  }
}

class ExternalApiAuthError extends AppError {
  constructor(message = 'Fallo de autenticación con la API externa de Football-Data.org (token inválido o no configurado).') {
    super(message, 502, 'EXTERNAL_API_AUTH_ERROR');
  }
}

const formatErrorResponse = (err) => {
  const status = err.status || 500;
  const code = err.code || 'INTERNAL_ERROR';
  const message = err.message || 'Ha ocurrido un error inesperado.';

  const response = {
    error: code,
    message,
    status,
    timestamp: new Date().toISOString()
  };

  if (err.details) {
    response.details = err.details;
  }

  return response;
};

module.exports = {
  AppError,
  UnauthorizedError,
  InvalidRequestParamsError,
  PlayerNotFoundError,
  MatchNotFoundError,
  ScrapeTimeoutError,
  ScrapeBlockedError,
  RateLimitExceededError,
  ExternalApiAuthError,
  formatErrorResponse
};
