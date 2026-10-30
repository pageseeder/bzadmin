import { cpuSample, cpuUsage, formatShortDuration } from '../lib/cpu.js';
import { clearFlash, dim, flash, on } from '../lib/dom.js';
import { formatPercent, formatSize } from '../lib/format.js';
import { fetchDocument, reloadFragment, swapFragment } from '../lib/http.js';
import { poller } from '../lib/poll.js';
import { formatRate, threadDeltas } from '../lib/threads.js';

/** How long a live chart runs before it stops by itself, in ms. */
const LIVE_DURATION = 5 * 60 * 1000;

/**
 * Wire the start/stop button of a live chart container to a poller calling `update(url, signal)`,
 * which stops by itself after `LIVE_DURATION`.
 *
 * @returns the poller, or `null` if the container has no start/stop button
 */
function livePoll(container, update) {
  const toggle = container.querySelector('.toggle-poll');
  if (!toggle) return null;
  const setState = (on) => {
    container.classList.toggle('on', on);
    toggle.querySelector('svg-icon')?.setAttribute('name', on ? 'pause-circle' : 'play-circle');
    const label = toggle.querySelector('span');
    if (label) label.textContent = on ? 'Stop' : 'Start';
  };
  const poll = poller(
    async (signal) => {
      await update(toggle.dataset.href, signal);
      clearFlash(toggle);
    },
    1000,
    {
      onError: (error) => {
        setState(false);
        flash(toggle, `Live update stopped: ${error.message}`, { type: 'error' });
      },
      duration: LIVE_DURATION,
      onEnd: () => {
        setState(false);
        flash(toggle, `Live update stopped after ${LIVE_DURATION / 60000} minutes`, { timeout: 6000 });
      }
    }
  );
  toggle.addEventListener('click', () => {
    if (poll.running) poll.stop();
    else poll.start();
    setState(poll.running);
  });
  return poll;
}

/** Get the named attributes, as numbers, of the first element matching `selector` in `doc`. */
function attributes(doc, selector, ...names) {
  const element = doc.querySelector(selector);
  if (!element) throw new Error(`No '${selector}' element in response`);
  return names.map((name) => Number(element.getAttribute(name)));
}

const page = document.querySelector('.page-bzadmin-system');
if (page) {
  const details = page.querySelector('#system-details');

  // The pollers of the live panels of the current view
  let polls = [];

  // Update a key metric of the current view, if it has it: its value, meter and detail
  const setMetric = (id, { value, meter, detail }) => {
    const metric = page.querySelector(`#${id}`);
    if (!metric) return;
    const cell = metric.querySelector('.key-metric-value');
    if (value !== undefined && cell) {
      cell.textContent = value;
      cell.classList.remove('muted');
      cell.removeAttribute('title');
    }
    const gauge = metric.querySelector('meter');
    if (meter !== undefined && gauge) gauge.value = meter;
    const text = metric.querySelector('.key-metric-detail');
    if (detail !== undefined && text) text.textContent = detail;
  };
  const readAt = (source) => {
    const note = page.querySelector('#metrics-read-at');
    if (note) note.textContent = `, read at ${new Date().toLocaleTimeString()}${source ? ` ${source}` : ''}`;
  };

  // Read the key metrics of the current view again, from the URL of their section
  const refreshMetrics = async (anchor) => {
    const metrics = page.querySelector('#system-metrics');
    if (!metrics) return;
    const doc = await reloadFragment(metrics.dataset.href, '#system-metrics', metrics, {
      anchor,
      error: 'Unable to refresh the key metrics'
    });
    if (doc) readAt();
  };

  // Live update of the heap, and of its key metrics
  const liveMemory = (memoryLive) => {
    const chart = memoryLive.querySelector('line-chart');
    const cells = Object.fromEntries(
      ['total-live-mb', 'total-live-pct', 'used-live-mb', 'used-live-pct'].map((id) => [
        id,
        memoryLive.querySelector(`#${id}`)
      ])
    );
    return livePoll(memoryLive, async (url, signal) => {
      const doc = await fetchDocument(url, 'application/xml', { signal });
      const [free, total, max] = attributes(doc, 'memory', 'free', 'total', 'max');
      const used = total - free;
      chart.push((total * 100) / max, (used * 100) / max);
      cells['total-live-mb'].textContent = (total / 1000000).toFixed(1);
      cells['total-live-pct'].textContent = formatPercent(total / max);
      cells['used-live-mb'].textContent = (used / 1000000).toFixed(1);
      cells['used-live-pct'].textContent = formatPercent(used / max);
      setMetric('metric-heap-used', {
        value: formatPercent(used / max),
        meter: used / max,
        detail: `${formatSize(used)} of ${formatSize(max)}`
      });
      setMetric('metric-heap-committed', {
        value: formatPercent(total / max),
        meter: total / max,
        detail: `${formatSize(total)} of ${formatSize(max)}`
      });
      readAt('from the live chart');
    });
  };

  // Live update of CPU usage, from the counters of successive samples, and of its key metrics
  const liveCpu = (cpuLive) => {
    const chart = cpuLive.querySelector('line-chart');
    const cells = ['machine', 'process', 'gc'].map((name) => [name, cpuLive.querySelector(`#cpu-live-${name}`)]);
    const percent = (value) => `${value.toFixed(1)}%`;
    let previous = null;
    return livePoll(cpuLive, async (url, signal) => {
      const doc = await fetchDocument(url, 'application/xml', { signal });
      const element = doc.querySelector('cpu-sample');
      if (!element) throw new Error("No 'cpu-sample' element in response");
      const sample = cpuSample(element);
      const usage = cpuUsage(previous, sample);
      // After a pause, the process and GC usage start again with a gap
      chart.push(usage.machine, usage.process, usage.gc);
      previous = sample;
      for (const [name, cell] of cells) cell.textContent = usage[name] === null ? '--' : percent(usage[name]);
      // The key metrics keep the last values read when the chart stops
      if (usage.process !== null) {
        setMetric('metric-cpu', {
          value: percent(usage.process),
          meter: usage.process / 100,
          detail: usage.machine === null ? undefined : `Machine ${percent(usage.machine)}`
        });
      }
      if (Number.isFinite(sample.loadAverage)) {
        setMetric('metric-load-average', {
          value: String(sample.loadAverage),
          meter: Math.min(sample.loadAverage, sample.processors)
        });
      }
      if (Number.isFinite(sample.processTime) && sample.uptime > 0) {
        setMetric('metric-cpu-time', {
          value: formatShortDuration(sample.processTime / 1e6),
          detail: `Average ${percent(sample.processTime / (sample.uptime * 1e4 * sample.processors))} since start`
        });
      }
      if (Number.isFinite(sample.gcTime) && sample.uptime > 0) {
        setMetric('metric-gc-time', { value: percent((sample.gcTime * 100) / sample.uptime) });
      }
      readAt('from the live chart');
    });
  };

  // Wire the live panels of the current view, after stopping those of the previous one
  const startLivePanels = () => {
    for (const poll of polls) poll.stop();
    const memoryLive = page.querySelector('#memory-live');
    const cpuLive = page.querySelector('#cpu-live');
    polls = [memoryLive && liveMemory(memoryLive), cpuLive && liveCpu(cpuLive)].filter(Boolean);
  };
  startLivePanels();

  // Read the key metrics of the current view again
  on(page, 'click', '#refresh-metrics', (event, button) => refreshMetrics(button));

  // Show file details
  on(page, 'click', '#files-details [data-href]', (event, button) => {
    reloadFragment(button.dataset.href, '#files-details', page.querySelector('#files-details'), {
      anchor: button,
      error: 'Unable to load file details'
    });
  });

  // Threads, on demand: the delta columns compare the counters of each thread with the previous refresh
  let counters = null;
  let lastRefresh = 0;

  // Fill the delta cells of the rows in `tbody`, before they are sorted
  const loadCounters = (tbody) => {
    const now = Date.now();
    const number = (value) => (value ? Number(value) : undefined);
    const current = new Map();
    for (const row of tbody.rows) {
      current.set(row.dataset.id, {
        cpu: number(row.querySelector('.cpu-delta')?.dataset.cpu),
        allocated: number(row.querySelector('.allocated-delta')?.dataset.allocated)
      });
    }
    if (counters !== null) {
      const elapsed = now - lastRefresh;
      const deltas = threadDeltas(counters, current, elapsed);
      page.querySelector('#since-last-refresh').textContent = ` Δ: ${(elapsed / 1000).toFixed(1)} seconds`;
      const show = (cell, value, format) => {
        if (!cell || value === null) return;
        cell.textContent = format(value);
        cell.dataset.sort = String(value);
      };
      for (const row of tbody.rows) {
        const delta = deltas.get(row.dataset.id);
        show(row.querySelector('.cpu-delta'), delta.cpu, (value) => `${value.toFixed(1)}%`);
        show(row.querySelector('.allocated-delta'), delta.allocated, formatRate);
      }
    }
    counters = current;
    lastRefresh = now;
  };

  // When switching between the overview, the CPU and threads, the memory and the file system
  on(page, 'click', '.secondary-nav a', async (event, link) => {
    event.preventDefault();
    const doc = await reloadFragment(link.href, '#system-details', details, {
      anchor: link.closest('.page-header'),
      error: 'Unable to load system details'
    });
    if (!doc) return;
    counters = null;
    startLivePanels();
    loadThreadsView();
    for (const choice of page.querySelectorAll('.secondary-nav a')) {
      choice.classList.toggle('active', choice === link);
      if (choice === link) choice.setAttribute('aria-current', 'page');
      else choice.removeAttribute('aria-current');
    }
    history.replaceState(null, '', link.href);
  });

  // Show the threads: when the Threads view opens, or with its button
  const showThreads = async (button) => {
    const threadsDetails = page.querySelector('#threads-details');
    const doc = await reloadFragment(button.dataset.href, '#threads-details', threadsDetails, {
      anchor: button,
      error: 'Unable to load threads'
    });
    const table = doc && threadsDetails.querySelector('.threads-table');
    if (table) loadCounters(table.tBodies[0]);
  };
  const loadThreadsView = () => {
    const button = page.querySelector('#threads-details > [data-href]');
    if (button) showThreads(button);
  };
  on(page, 'click', '#threads-details > [data-href]', (event, button) => showThreads(button));
  loadThreadsView();

  // Refresh the threads: the key metrics of the view, the states and origins, and the table (keeping its sort)
  on(page, 'click', '#refresh-threads', async (event, button) => {
    const threads = page.querySelector('.threads-table');
    const overview = page.querySelector('#threads-overview');
    if (!threads || !overview) return;
    const metrics = refreshMetrics(button);
    dim(threads, true);
    dim(overview, true);
    try {
      const doc = await fetchDocument(button.dataset.href);
      const tbody = doc.querySelector('.threads-table tbody');
      const states = doc.querySelector('#threads-overview');
      if (!tbody || !states) throw new Error(`No threads in ${button.dataset.href}`);
      loadCounters(tbody);
      threads.closest('sortable-table').replaceRows(tbody);
      overview.replaceChildren(...document.adoptNode(states).childNodes);
      clearFlash(button);
    } catch (error) {
      flash(button, `Unable to refresh threads: ${error.message}`, { type: 'error' });
    } finally {
      dim(threads, false);
      dim(overview, false);
    }
    await metrics;
  });

  // Idle threads stay hidden after a refresh: the class is on the container
  on(page, 'change', '#hide-idle-threads', (event, input) => {
    page.querySelector('#threads-details')?.classList.toggle('hide-idle', input.checked);
  });

  // The stack of a thread, in the dialog of the threads table
  on(page, 'click', '.show-thread', async (event, button) => {
    const dialog = page.querySelector('#thread-dialog');
    if (!dialog) return;
    try {
      await swapFragment(button.dataset.href, '#thread-info', dialog.querySelector('.thread-dialog-content'));
      dialog.querySelector('.dialog-title').textContent = button.getAttribute('aria-label');
      clearFlash(button);
      dialog.showModal();
    } catch (error) {
      const message = error.status === 404 ? 'This thread has ended' : `Unable to load the thread: ${error.message}`;
      flash(button, message, { type: 'error' });
    }
  });
}
