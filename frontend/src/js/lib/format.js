/**
 * Formats shared by the pages, matching those of the stylesheets.
 */

/**
 * @param {number} bytes
 * @returns {string} the size in decimal units with one decimal at most, as `f:to-size` writes it (`52.4 MB`)
 */
export function formatSize(bytes) {
  if (bytes < 1000) return `${Math.round(bytes)} bytes`;
  const units = ['KB', 'MB', 'GB', 'TB'];
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
