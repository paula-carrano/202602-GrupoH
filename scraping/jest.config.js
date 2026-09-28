const path = require('node:path');

module.exports = {
  collectCoverageFrom: ['src/**/*.js'],
  coverageDirectory: 'coverage',
  coverageReporters: [
    'text-summary',
    ['lcov', { projectRoot: path.resolve(__dirname, '..') }],
  ],
};
