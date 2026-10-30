/**
 * Deltas of the cumulative counters of each thread (CPU time, allocated bytes) between two
 * refreshes of the threads table.
 */

/**
 * @typedef {{cpu?: number, allocated?: number}} ThreadCounters the CPU time in nanoseconds and the
 *   allocated bytes of a thread since it started, without those that are not available
 */

/**
 * @param {Map<string, ThreadCounters>|null} previous the counters of the previous refresh, by thread ID
 * @param {Map<string, ThreadCounters>} current the counters of this refresh, by thread ID
 * @param {number} elapsed the time between the two refreshes, in ms
 * @returns {Map<string, {cpu: number|null, allocated: number|null}>} for each thread of `current`:
 *   the CPU it used as a percentage of one processor, and the bytes it allocated per second;
 *   `null` when unknown (no previous refresh, a new thread, a counter not available or lower)
 */
export function threadDeltas(previous, current, elapsed) {
  const deltas = new Map();
  for (const [id, counters] of current) {
    const before = previous?.get(id);
    const delta = (name) => {
      const value = counters[name] - before?.[name];
      return elapsed > 0 && Number.isFinite(value) && value >= 0 ? value : null;
    };
    const cpu = delta('cpu');
    const allocated = delta('allocated');
    deltas.set(id, {
      // Nanoseconds of CPU over milliseconds of one processor
      cpu: cpu === null ? null : cpu / (elapsed * 1e4),
      allocated: allocated === null ? null : (allocated * 1000) / elapsed
    });
  }
  return deltas;
}

/**
 * @param {number} bytes per second
 * @returns {string} the rate in decimal units, as the page shows sizes (`1.2 MB/s`)
 */
export function formatRate(bytes) {
  if (bytes < 1000) return `${Math.round(bytes)} B/s`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1000;
  let unit = 0;
  while (value >= 1000 && unit < units.length - 1) {
    value /= 1000;
    unit += 1;
  }
  return `${value.toFixed(1)} ${units[unit]}/s`;
}
