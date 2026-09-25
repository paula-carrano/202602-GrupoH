const playerScraper = require('../services/whoscored/playerScraper');

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

module.exports = {
  getPlayerStats
};
