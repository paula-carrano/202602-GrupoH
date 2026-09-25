const apiClient = require('../services/footballData/apiClient');

const getCompetitionMatches = async (req, res, next) => {
  const { competitionCode } = req.params;
  const { dateFrom, dateTo } = req.query;

  try {
    const matches = await apiClient.getCompetitionMatches(competitionCode.toUpperCase(), dateFrom, dateTo);
    res.status(200).json(matches);
  } catch (err) {
    next(err);
  }
};

const getMatchDetail = async (req, res, next) => {
  const { matchId } = req.params;

  try {
    const match = await apiClient.getMatchById(matchId);
    res.status(200).json(match);
  } catch (err) {
    next(err);
  }
};

module.exports = {
  getCompetitionMatches,
  getMatchDetail
};
