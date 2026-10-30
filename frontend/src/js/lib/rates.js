/**
 * Rates of cumulative counters (the hits, puts… of a cache) over a sliding window of samples, so
 * that the page shows what happens now rather than since the counters started.
 *
 * A counter that decreases means the counters were reset: the window starts again from that sample.
 *
 * @param {number} [span=60000] the length of the window in ms
 */
export function counterWindow(span = 60000) {
  let samples = [];
  return {
    /**
     * @param {number} time in ms
     * @param {Record<string, number>} counters the counters available at that time
     * @returns {boolean} `true` if the counters were reset since the previous sample
     */
    add(time, counters) {
      const last = samples.at(-1);
      const reset = last !== undefined && Object.keys(counters).some((name) => counters[name] < last.counters[name]);
      if (reset) samples = [];
      samples.push({ time, counters });
      // Keep the most recent sample older than the window, so that it spans the whole window
      while (samples.length > 2 && samples[1].time <= time - span) samples.shift();
      return reset;
    },

    /**
     * @returns {{seconds: number, deltas: Record<string, number>} | null} how much each counter
     *   available in the first and last samples increased, or `null` before two samples
     */
    rates() {
      if (samples.length < 2) return null;
      const first = samples[0];
      const last = samples.at(-1);
      const deltas = {};
      for (const name of Object.keys(last.counters)) {
        if (name in first.counters) deltas[name] = last.counters[name] - first.counters[name];
      }
      return { seconds: (last.time - first.time) / 1000, deltas };
    }
  };
}

/**
 * @param {number} count
 * @param {number} seconds
 * @returns {string} the rate per second, or per minute when less than one per second
 */
export function formatRate(count, seconds) {
  const rate = count / seconds;
  if (count === 0) return 'None';
  if (rate >= 1) return `${rate.toFixed(1)}/s`;
  return `${(rate * 60).toFixed(1)}/min`;
}
