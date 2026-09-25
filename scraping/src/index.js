const express = require('express');
const config = require('./config/env');
const logger = require('./middleware/logger');
const errorHandler = require('./middleware/errorHandler');
const authMiddleware = require('./middleware/auth');

const app = express();

app.use(express.json());
app.use(logger);

// Endpoint público de verificación de salud para Docker / orquestadores (bypasses auth)
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'UP',
    timestamp: new Date().toISOString()
  });
});

// A partir de aquí todas las rutas de negocio requieren autenticación transversal X-API-Key
app.use(authMiddleware);

// Rutas de Jugadores (User Story 1 - MVP)
const playerController = require('./controllers/playerController');
app.get('/players/:whoscoredId/stats', playerController.getPlayerStats);

// Rutas de Competiciones y Partidos (User Story 2)
const matchController = require('./controllers/matchController');
const lineupController = require('./controllers/lineupController');
const {
  validateCompetitionMatches,
  validateMatchDetail,
  validateLineupParams
} = require('./middleware/validator');

app.get('/competitions/:competitionCode/matches', validateCompetitionMatches, matchController.getCompetitionMatches);
app.get('/matches/:matchId', validateMatchDetail, matchController.getMatchDetail);
const catalogController = require('./controllers/catalogController');
app.get('/competitions/:competitionCode/teams', validateCompetitionMatches, catalogController.getTeams);
app.get('/teams/:teamId/squad', catalogController.getSquad);
app.get('/lineups', validateLineupParams, lineupController.getLineups);

// Manejador centralizado de errores
app.use(errorHandler);

if (process.env.NODE_ENV !== 'test') {
  app.listen(config.PORT, () => {
    console.log(`[Scraper Service] Listening on port ${config.PORT}`);
  });
}

module.exports = app;
