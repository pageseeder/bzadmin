/**
 * Formats shared by the pages, matching those of the stylesheets.
 */

const COUNT_FORMATS = new Map();

/**
 * @param {number} value
 * @param {number} [decimals] the number of decimals, none by default
 * @returns {string} the number with thousands separators, as `f:count` writes it (`1,234`)
 */
export function formatCount(value, decimals = 0) {
  if (!COUNT_FORMATS.has(decimals)) {
    COUNT_FORMATS.set(
      decimals,
      new Intl.NumberFormat('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
    );
  }
  return COUNT_FORMATS.get(decimals).format(value);
}

/**
 * @param {number} bytes
 * @returns {string} the size in decimal units with one decimal at most, as `f:to-size` writes it (`52.4 MB`)
 */
export function formatSize(bytes) {
  if (bytes < 1000) return `${Math.round(bytes)} bytes`;
  const units = ['KB', 'MB', 'GB', 'TB', 'PB'];
  let value = bytes / 1000;
  let unit = 0;
  while (value >= 1000 && unit < units.length - 1) {
    value /= 1000;
    unit += 1;
  }
  return `${Number(value.toFixed(1))} ${units[unit]}`;
}

/**
 * @param {number} fraction from 0 to 1
 * @returns {string} the percentage with one decimal, as `f:to-percent` writes it (`2.1%`)
 */
export function formatPercent(fraction) {
  return `${(fraction * 100).toFixed(1)}%`;
}
