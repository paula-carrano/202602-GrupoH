const { InvalidRequestParamsError } = require('../utils/errors');

const VALID_COMPETITIONS = ['PL', 'BL1', 'PD', 'SA', 'FL1'];
const DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;

const validateCompetitionMatches = (req, res, next) => {
  const { competitionCode } = req.params;
  const { dateFrom, dateTo } = req.query;

  if (!competitionCode || !VALID_COMPETITIONS.includes(competitionCode.toUpperCase())) {
    return next(new InvalidRequestParamsError(`competitionCode debe ser uno de: ${VALID_COMPETITIONS.join(', ')}`));
  }

  if (dateFrom && !DATE_REGEX.test(dateFrom)) {
    return next(new InvalidRequestParamsError('dateFrom debe tener formato ISO YYYY-MM-DD'));
  }

  if (dateTo && !DATE_REGEX.test(dateTo)) {
    return next(new InvalidRequestParamsError('dateTo debe tener formato ISO YYYY-MM-DD'));
  }

  next();
};

const validateMatchDetail = (req, res, next) => {
  const { matchId } = req.params;
  if (!matchId || isNaN(parseInt(matchId, 10))) {
    return next(new InvalidRequestParamsError('matchId debe ser un número entero'));
  }
  next();
};

const validateLineupParams = (req, res, next) => {
  const { homeTeam, awayTeam, date } = req.query;

  if (!homeTeam || typeof homeTeam !== 'string' || homeTeam.trim() === '') {
    return next(new InvalidRequestParamsError('homeTeam es requerido'));
  }

  if (!awayTeam || typeof awayTeam !== 'string' || awayTeam.trim() === '') {
    return next(new InvalidRequestParamsError('awayTeam es requerido'));
  }

  if (!date || !DATE_REGEX.test(date)) {
    return next(new InvalidRequestParamsError('date es requerido con formato ISO YYYY-MM-DD'));
  }

  next();
};

module.exports = {
  VALID_COMPETITIONS,
  validateCompetitionMatches,
  validateMatchDetail,
  validateLineupParams
};
