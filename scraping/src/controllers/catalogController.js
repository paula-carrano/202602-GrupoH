const api = require('../services/footballData/apiClient');
const { InvalidRequestParamsError } = require('../utils/errors');

const getTeams = async (req, res, next) => {
  try {
    res.json(await api.getCompetitionTeams(req.params.competitionCode.toUpperCase()));
  } catch (error) { next(error); }
};

const getSquad = async (req, res, next) => {
  const id = Number(req.params.teamId);
  if (!Number.isSafeInteger(id) || id <= 0) {
    return next(new InvalidRequestParamsError('teamId debe ser un entero positivo'));
  }
  try {
    res.json(await api.getTeamSquad(id));
  } catch (error) { next(error); }
};

module.exports = { getTeams, getSquad };
