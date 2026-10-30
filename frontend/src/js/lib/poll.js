/**
 * Call `fn` repeatedly, waiting `interval` ms after each call completes so that slow responses
 * never overlap. `fn` receives an `AbortSignal` that is aborted when polling stops.
 *
 * Polling pauses while the page is hidden and resumes when it is visible again. If `fn` throws,
 * polling stops and `onError` is called. With a `duration`, polling stops that many ms after it
 * started, hidden time included, and `onEnd` is called.
 *
 * @param {(signal: AbortSignal) => Promise<void>} fn
 * @param {number} interval
 * @param {object} [options]
 * @param {(error: Error) => void} [options.onError]
 * @param {number} [options.duration] in ms, after which polling stops
 * @param {() => void} [options.onEnd] called when polling stops after `duration`
 * @param {Document} [options.doc] the document whose visibility pauses polling
 */
export function poller(fn, interval, { onError, duration, onEnd, doc = document } = {}) {
  let running = false;
  let controller = null;
  let timer = 0;
  let end = 0;

  const loop = async (signal) => {
    try {
      await fn(signal);
    } catch (error) {
      if (signal.aborted) return;
      api.stop();
      onError?.(error);
      return;
    }
    if (!signal.aborted) timer = setTimeout(() => loop(signal), interval);
  };

  const resume = () => {
    if (controller) return;
    controller = new AbortController();
    loop(controller.signal);
  };

  const pause = () => {
    controller?.abort();
    controller = null;
    clearTimeout(timer);
  };

  doc.addEventListener('visibilitychange', () => {
    if (!running) return;
    if (doc.hidden) pause();
    else resume();
  });

  const api = {
    get running() {
      return running;
    },
    start() {
      running = true;
      clearTimeout(end);
      if (duration) {
        end = setTimeout(() => {
          api.stop();
          onEnd?.();
        }, duration);
      }
      if (!doc.hidden) resume();
    },
    stop() {
      running = false;
      clearTimeout(end);
      pause();
    }
  };
  return api;
}
