const lineupScraper = require('../services/whoscored/lineupScraper');

const getLineups = async (req, res, next) => {
  const { homeTeam, awayTeam, date } = req.query;

  const controller = new AbortController();
  req.on('close', () => {
    if (!res.writableEnded) {
      controller.abort();
    }
  });

  try {
    const lineups = await lineupScraper.scrapeLineup(homeTeam, awayTeam, date, controller.signal);
    res.status(200).json(lineups);
  } catch (err) {
    if (controller.signal.aborted) {
      return;
    }
    next(err);
  }
};

module.exports = {
  getLineups
};
