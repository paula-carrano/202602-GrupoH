const nock = require('nock');

const FOOTBALL_DATA_BASE_URL = 'https://api.football-data.org/v4';

/**
 * Helper to mock Football-Data.org endpoints cleanly and hermetically
 */
const mockFootballData = {
  getCompetitionMatches: (competitionCode, responseBody, statusCode = 200, query = {}) => {
    return nock(FOOTBALL_DATA_BASE_URL)
      .get(`/competitions/${competitionCode}/matches`)
      .query(query)
      .reply(statusCode, responseBody);
  },

  getMatch: (matchId, responseBody, statusCode = 200) => {
    return nock(FOOTBALL_DATA_BASE_URL)
      .get(`/matches/${matchId}`)
      .reply(statusCode, responseBody);
  },

  cleanAll: () => {
    nock.cleanAll();
  }
};

module.exports = mockFootballData;
