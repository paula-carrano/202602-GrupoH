const axios = require('axios');
const config = require('../../config/env');
const { limiter } = require('./rateLimiter');
const {
  MatchNotFoundError,
  RateLimitExceededError,
  ExternalApiAuthError
} = require('../../utils/errors');

const FOOTBALL_DATA_BASE_URL = 'https://api.football-data.org/v4';

const client = axios.create({
  baseURL: FOOTBALL_DATA_BASE_URL,
  timeout: 10000
});

// Interceptor para inyectar token de autenticación
client.interceptors.request.use((reqConfig) => {
  if (config.FOOTBALL_DATA_API_KEY) {
    reqConfig.headers['X-Auth-Token'] = config.FOOTBALL_DATA_API_KEY;
  }
  return reqConfig;
});

// Interceptor de errores para mapeo estricto
client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const status = error.response.status;
      if (status === 404) {
        throw new MatchNotFoundError('No se encontró el partido o recurso especificado en Football-Data.org.');
      }
      if (status === 429) {
        throw new RateLimitExceededError();
      }
      if (status === 401 || status === 403) {
        throw new ExternalApiAuthError();
      }
    }
    throw error;
  }
);

/**
 * Normaliza el payload de Football-Data.org al modelo MatchFixture
 */
const normalizeMatch = (match) => {
  return {
    id: match.id,
    competition: match.competition ? match.competition.code || match.competition.name : undefined,
    utcDate: match.utcDate,
    status: match.status,
    matchday: match.matchday,
    homeTeam: {
      id: match.homeTeam ? match.homeTeam.id : null,
      name: match.homeTeam ? match.homeTeam.name : ''
    },
    awayTeam: {
      id: match.awayTeam ? match.awayTeam.id : null,
      name: match.awayTeam ? match.awayTeam.name : ''
    },
    score: match.score || null
  };
};

const getCompetitionMatches = async (competitionCode, dateFrom, dateTo) => {
  return limiter.schedule(async () => {
    const params = {};
    if (dateFrom) params.dateFrom = dateFrom;
    if (dateTo) params.dateTo = dateTo;

    const response = await client.get(`/competitions/${competitionCode}/matches`, { params });
    const rawMatches = response.data.matches || [];
    return rawMatches.map((m) => {
      const norm = normalizeMatch(m);
      if (!norm.competition) norm.competition = competitionCode;
      return norm;
    });
  });
};

const getMatchById = async (matchId) => {
  return limiter.schedule(async () => {
    const response = await client.get(`/matches/${matchId}`);
    return normalizeMatch(response.data);
  });
};

module.exports = {
  client,
  getCompetitionMatches,
  getMatchById,
  normalizeMatch
};
