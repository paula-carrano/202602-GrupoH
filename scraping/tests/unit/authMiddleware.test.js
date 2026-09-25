const authMiddleware = require('../../src/middleware/auth');
const config = require('../../src/config/env');
const { UnauthorizedError } = require('../../src/utils/errors');

describe('authMiddleware', () => {
  let req;
  let res;
  let next;

  beforeEach(() => {
    req = {
      header: jest.fn()
    };
    res = {};
    next = jest.fn();
  });

  it('should call next() without error when valid X-API-Key is provided', () => {
    req.header.mockReturnValue(config.API_KEY);

    authMiddleware(req, res, next);

    expect(req.header).toHaveBeenCalledWith('X-API-Key');
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith();
  });

  it('should call next(UnauthorizedError) when X-API-Key is missing', () => {
    req.header.mockReturnValue(undefined);

    authMiddleware(req, res, next);

    expect(next).toHaveBeenCalledTimes(1);
    const err = next.mock.calls[0][0];
    expect(err).toBeInstanceOf(UnauthorizedError);
    expect(err.status).toBe(401);
    expect(err.code).toBe('UNAUTHORIZED');
  });

  it('should call next(UnauthorizedError) when X-API-Key is invalid', () => {
    req.header.mockReturnValue('wrong-key-value');

    authMiddleware(req, res, next);

    expect(next).toHaveBeenCalledTimes(1);
    const err = next.mock.calls[0][0];
    expect(err).toBeInstanceOf(UnauthorizedError);
    expect(err.status).toBe(401);
    expect(err.code).toBe('UNAUTHORIZED');
  });
});
