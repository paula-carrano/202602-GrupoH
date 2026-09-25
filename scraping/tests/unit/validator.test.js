const {
  validateCompetitionMatches,
  validateMatchDetail,
  validateLineupParams
} = require('../../src/middleware/validator');
const { InvalidRequestParamsError } = require('../../src/utils/errors');

describe('validator middleware unit tests', () => {
  let next;

  beforeEach(() => {
    next = jest.fn();
  });

  describe('validateCompetitionMatches', () => {
    it('should accept valid competition code and dates', () => {
      const req = {
        params: { competitionCode: 'PL' },
        query: { dateFrom: '2026-09-01', dateTo: '2026-09-30' }
      };
      validateCompetitionMatches(req, {}, next);
      expect(next).toHaveBeenCalledWith();
    });

    it('should reject invalid competition code', () => {
      const req = {
        params: { competitionCode: 'INVALID' },
        query: {}
      };
      validateCompetitionMatches(req, {}, next);
      const err = next.mock.calls[0][0];
      expect(err).toBeInstanceOf(InvalidRequestParamsError);
      expect(err.status).toBe(400);
      expect(err.code).toBe('INVALID_REQUEST_PARAMS');
    });

    it('should reject invalid date format', () => {
      const req = {
        params: { competitionCode: 'PL' },
        query: { dateFrom: '01-09-2026' }
      };
      validateCompetitionMatches(req, {}, next);
      expect(next.mock.calls[0][0]).toBeInstanceOf(InvalidRequestParamsError);
    });
  });

  describe('validateMatchDetail', () => {
    it('should accept numeric matchId', () => {
      const req = { params: { matchId: '432501' } };
      validateMatchDetail(req, {}, next);
      expect(next).toHaveBeenCalledWith();
    });

    it('should reject non-numeric matchId', () => {
      const req = { params: { matchId: 'abc' } };
      validateMatchDetail(req, {}, next);
      expect(next.mock.calls[0][0]).toBeInstanceOf(InvalidRequestParamsError);
    });
  });

  describe('validateLineupParams', () => {
    it('should accept valid homeTeam, awayTeam and date', () => {
      const req = {
        query: { homeTeam: 'Arsenal', awayTeam: 'Chelsea', date: '2026-09-20' }
      };
      validateLineupParams(req, {}, next);
      expect(next).toHaveBeenCalledWith();
    });

    it('should reject missing homeTeam', () => {
      const req = {
        query: { awayTeam: 'Chelsea', date: '2026-09-20' }
      };
      validateLineupParams(req, {}, next);
      expect(next.mock.calls[0][0]).toBeInstanceOf(InvalidRequestParamsError);
    });
  });
});
