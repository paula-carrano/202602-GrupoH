/**
 * Coerces raw scraped strings/values to valid numbers.
 * Converts dashes ('-'), empty strings, null, undefined or NaN to 0 or 0.0.
 */
const toInt = (val) => {
  if (val === null || val === undefined) return 0;
  const cleaned = String(val).replace(/,/g, '').trim();
  if (cleaned === '' || cleaned === '-' || cleaned === 'N/A') return 0;
  const num = parseInt(cleaned, 10);
  return Number.isNaN(num) ? 0 : num;
};

const toFloat = (val) => {
  if (val === null || val === undefined) return 0.0;
  const cleaned = String(val).replace(/,/g, '').trim();
  if (cleaned === '' || cleaned === '-' || cleaned === 'N/A') return 0.0;
  const num = parseFloat(cleaned);
  return Number.isNaN(num) ? 0.0 : Math.round(num * 100) / 100;
};

module.exports = {
  toInt,
  toFloat
};
