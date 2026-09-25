const fs = require('fs');
const path = require('path');
const { parsePlayerStatsFromHtml } = require('../../src/services/whoscored/playerScraper');

describe('playerScraper unit parsing', () => {
  it('should parse player-stats.html accurately into 10 quantitative fields', () => {
    const htmlPath = path.resolve(__dirname, '../fixtures/whoscored/player-stats.html');
    const html = fs.readFileSync(htmlPath, 'utf8');

    const stats = parsePlayerStatsFromHtml(html);

    expect(stats).toEqual({
      goals: 10,
      assists: 5,
      shots: 40,
      keyPasses: 28,
      dribbles: 22,
      tackles: 12,
      rating: 7.42,
      minutosJugados: 1620,
      tarjetasAmarillas: 2,
      tarjetasRojas: 0
    });
  });

  it('should parse player-empty.html normalizing dashes and missing values to numeric 0', () => {
    const htmlPath = path.resolve(__dirname, '../fixtures/whoscored/player-empty.html');
    const html = fs.readFileSync(htmlPath, 'utf8');

    const stats = parsePlayerStatsFromHtml(html);

    expect(stats).toEqual({
      goals: 0,
      assists: 0,
      shots: 0,
      keyPasses: 0,
      dribbles: 0,
      tackles: 0,
      rating: 0.0,
      minutosJugados: 0,
      tarjetasAmarillas: 0,
      tarjetasRojas: 0
    });
  });

  it('should parse live-player.html (real WhoScored structure for Harry Kane) accurately into 10 quantitative fields', () => {
    const htmlPath = path.resolve(__dirname, '../fixtures/whoscored/live-player.html');
    const html = fs.readFileSync(htmlPath, 'utf8');

    const stats = parsePlayerStatsFromHtml(html);

    expect(stats).toEqual({
      goals: 21,
      assists: 4,
      shots: 84,
      keyPasses: 28,
      dribbles: 17,
      tackles: 13,
      rating: 7.66,
      minutosJugados: 836,
      tarjetasAmarillas: 1,
      tarjetasRojas: 0
    });
  });
});
