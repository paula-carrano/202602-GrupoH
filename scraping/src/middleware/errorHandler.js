const errorHandler = (err, req, res, next) => {
	const status = err.status || 500;
	const formatted = formatErrorResponse(err);

	if (process.env.NODE_ENV !== "test" && status >= 500) {
		const sanitizedMethod = req.method ? req.method.replace(/[\r\n]/g, "") : "";
		const sanitizedUrl = req.originalUrl
			? req.originalUrl.replace(/[\r\n]/g, "")
			: "";

		console.error(`[Error Handler] ${sanitizedMethod} ${sanitizedUrl}:`, err);
	}

	res.status(status).json(formatted);
};

module.exports = errorHandler;
