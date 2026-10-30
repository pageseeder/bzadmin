/**
 * CPU usage between two samples of the cumulative CPU counters of the JVM (`<cpu-sample>`), so
 * that the usage covers all the time between the samples.
 */

/** Usage over more than this time is not shown: the page was hidden or the chart stopped. */
export const MAX_ELAPSED = 5000;

/**
 * @param {Element} element a `<cpu-sample>` element
 * @returns {{processors: number, uptime: number, processTime?: number, gcTime?: number, systemLoad?: number, loadAverage?: number}}
 *   its values, without those it does not have
 */
export function cpuSample(element) {
  const number = (name) => (element.hasAttribute(name) ? Number(element.getAttribute(name)) : undefined);
  return {
    processors: number('processors'),
    uptime: number('uptime-ms'),
    processTime: number('process-time-ns'),
    gcTime: number('gc-time-ms'),
    systemLoad: number('system-load'),
    loadAverage: number('load-average')
  };
}

/**
 * @param {ReturnType<typeof cpuSample> | null} previous the previous sample
 * @param {ReturnType<typeof cpuSample>} sample the new sample
 * @returns {{machine: number|null, process: number|null, gc: number|null}} percentages:
 *   `machine` and `process` of all processors, `gc` of the elapsed time; `null` when unavailable
 *   or when the samples are not consecutive (none, too far apart, or from another JVM)
 */
export function cpuUsage(previous, sample) {
  const machine = Number.isFinite(sample.systemLoad) ? sample.systemLoad * 100 : null;
  const elapsed = previous ? sample.uptime - previous.uptime : NaN;
  if (!(elapsed > 0 && elapsed <= MAX_ELAPSED)) return { machine, process: null, gc: null };
  const rate = (name) => {
    const delta = sample[name] - previous[name];
    return Number.isFinite(delta) && delta >= 0 ? delta : null;
  };
  const processTime = rate('processTime');
  const gcTime = rate('gcTime');
  return {
    machine,
    // Nanoseconds of CPU over milliseconds of all processors
    process: processTime === null ? null : Math.min(processTime / (elapsed * 1e4 * sample.processors), 100),
    gc: gcTime === null ? null : Math.min((gcTime * 100) / elapsed, 100)
  };
}

/**
 * @param {number} ms
 * @returns {string} the duration in days, hours, minutes and seconds, as the page shows uptimes
 */
export function formatDuration(ms) {
  const s = Math.floor(ms / 1000);
  return [
    s >= 86400 ? `${Math.floor(s / 86400)}d` : null,
    s >= 3600 ? `${Math.floor((s % 86400) / 3600)}h` : null,
    s >= 60 ? `${Math.floor((s % 3600) / 60)}min` : null,
    `${s % 60}s`
  ]
    .filter(Boolean)
    .join(' ');
}

/**
 * @param {number} ms
 * @returns {string} the duration with its two largest units, as the key metrics show it (`1d 1h`, `5min 3s`)
 */
export function formatShortDuration(ms) {
  const s = Math.floor(ms / 1000);
  if (s >= 86400) return `${Math.floor(s / 86400)}d ${Math.floor((s % 86400) / 3600)}h`;
  if (s >= 3600) return `${Math.floor(s / 3600)}h ${Math.floor((s % 3600) / 60)}min`;
  if (s >= 60) return `${Math.floor(s / 60)}min ${s % 60}s`;
  return `${s}s`;
}
