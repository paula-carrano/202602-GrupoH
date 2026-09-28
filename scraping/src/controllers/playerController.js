const playerScraper = require('../services/whoscored/playerScraper');
const playerSearchScraper = require('../services/whoscored/playerSearchScraper');
const { InvalidRequestParamsError } = require('../utils/errors');

const getPlayerStats = async (req, res, next) => {
  const { whoscoredId } = req.params;

  // Controller listens to req.on('close') to abort pending page navigation if client disconnects
  const controller = new AbortController();
  req.on('close', () => {
    if (!res.writableEnded) {
      controller.abort();
    }
  });

  try {
    const stats = await playerScraper.scrapePlayerStats(whoscoredId, controller.signal);
    res.status(200).json(stats);
  } catch (err) {
    if (controller.signal.aborted) {
      return;
    }
    next(err);
  }
};

const searchPlayers = async (req, res, next) => {
  const query = typeof req.query.q === 'string' ? req.query.q.trim() : '';
  if (query.length < 2 || query.length > 120) {
    return next(new InvalidRequestParamsError('q debe tener entre 2 y 120 caracteres'));
  }

  const controller = new AbortController();
  req.on('close', () => {
    if (!res.writableEnded) controller.abort();
  });
  try {
    res.status(200).json(await playerSearchScraper.searchPlayers(query, controller.signal));
  } catch (error) {
    if (!controller.signal.aborted) next(error);
  }
};

const searchTeamPlayers = async (req, res, next) => {
  const teamName = typeof req.query.name === 'string' ? req.query.name.trim() : '';
  const country = typeof req.query.country === 'string' ? req.query.country.trim() : '';
  if (teamName.length < 2 || teamName.length > 120 || country.length > 80) {
    return next(new InvalidRequestParamsError('name debe tener entre 2 y 120 caracteres y country hasta 80'));
  }
  const controller = new AbortController();
  req.on('close', () => {
    if (!res.writableEnded) controller.abort();
  });
  try {
    res.status(200).json(await playerSearchScraper.searchTeamPlayers(teamName, country, controller.signal));
  } catch (error) {
    if (!controller.signal.aborted) next(error);
  }
};

module.exports = {
  getPlayerStats,
  searchPlayers,
  searchTeamPlayers
};
