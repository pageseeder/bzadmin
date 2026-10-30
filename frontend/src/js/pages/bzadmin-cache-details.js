import { clearFlash, fadeOut, flash, on } from '../lib/dom.js';
import { fetchDocument, post } from '../lib/http.js';
import { poller } from '../lib/poll.js';
import { counterWindow, formatRate } from '../lib/rates.js';

// How often the statistics are read for the recent activity, over how long rates are computed,
// and when reading stops by itself, for a page left open
const RATES_INTERVAL = 5000;
const RATES_SPAN = 60000;
const RATES_DURATION = 10 * 60000;

// The information and the keys of one cache
const page = document.querySelector('.page-bzadmin-cache-info, .page-bzadmin-cache-entries');
const section = page?.querySelector('section.cache-details');
const heading = section?.querySelector('.cache-identity');
if (heading) {
  // The cache, named by provider, manager and name
  const { provider, manager, cache } = heading.dataset;
  const identity = { provider, manager, cache };

  on(section, 'click', 'button.clear-cache', async (event, button) => {
    const scope = button.dataset.scope;
    const where = scope === 'local' ? '' : ` Its scope is ${scope}: this may affect other nodes.`;
    if (!confirm(`Are you sure you want to remove every entry of the cache ${cache}?${where}`)) return;
    try {
      await post(section.dataset.clear, identity);
      window.location.reload();
    } catch {
      flash(button, 'Unable to clear cache', { type: 'error' });
    }
  });

  on(section, 'click', 'button.toggle-statistics', async (event, button) => {
    try {
      await post(section.dataset.statistics, { ...identity, enable: button.dataset.enable });
      window.location.reload();
    } catch {
      flash(button, 'Unable to change statistics', { type: 'error' });
    }
  });

  // The counts start again from zero; the entries are kept
  on(section, 'click', 'button.reset-statistics', async (event, button) => {
    if (!confirm(`Reset the statistics of the cache ${cache}? The counts start again from zero; the entries are kept.`))
      return;
    try {
      await post(section.dataset.resetStatistics, identity);
      window.location.reload();
    } catch {
      flash(button, 'Unable to reset statistics', { type: 'error' });
    }
  });

  // Remove a listed key
  on(section, 'click', 'button.remove', async (event, button) => {
    const table = button.closest('table');
    button.disabled = true;
    try {
      await post(section.dataset.remove, { ...identity, key: button.dataset.key, 'key-type': button.dataset.keyType });
      const row = button.closest('tr');
      await fadeOut(row);
      row.remove();
      clearFlash(table);
    } catch {
      button.disabled = false;
      flash(table, 'Unable to remove entry', { type: 'error' });
    }
  });

  // Remove a key typed in (caches whose keys cannot be listed)
  on(section, 'submit', 'form.remove-entry', async (event, form) => {
    event.preventDefault();
    const key = form.elements.key.value;
    const type = form.elements['key-type'].value;
    const button = form.querySelector('button');
    try {
      const body = new URLSearchParams({ ...identity, key, 'key-type': type });
      const xml = await fetchDocument(section.dataset.remove, 'application/xml', { method: 'POST', body });
      const removed = xml.querySelector('remove-cache-entry')?.getAttribute('removed') === 'true';
      flash(button, removed ? 'Entry removed' : 'No entry with this key');
    } catch {
      flash(button, 'Unable to remove entry', { type: 'error' });
    }
  });

  const rates = section.querySelector('.cache-rates');
  if (rates) showRecentActivity(rates);
}

/**
 * Reads the statistics of the cache every few seconds, and shows the rates of its counters over
 * the last minute: the counts of the library may cover a long period. Reading starts with the
 * page, stops with the button or after `RATES_DURATION`, and pauses while the page is hidden.
 *
 * @param {HTMLElement} rates the `.cache-rates` block, with the XML endpoint in `data-source`
 */
function showRecentActivity(rates) {
  let samples = counterWindow(RATES_SPAN);
  let timer = 0;
  const button = rates.querySelector('button.toggle-rates');
  const status = rates.querySelector('.cache-rates-window');
  const cells = new Map([...rates.querySelectorAll('[data-rate]')].map((cell) => [cell.dataset.rate, cell]));
  // A rate of zero is muted, like the zero counts
  const show = (name, text, zero = false) => {
    const cell = cells.get(name);
    if (!cell) return;
    cell.textContent = text;
    cell.classList.toggle('muted', zero);
  };

  const render = (reset) => {
    const result = samples.rates();
    if (!result) {
      for (const name of cells.keys()) show(name, '—', true);
      status.textContent = reset ? 'The statistics were reset: measuring again…' : 'Measuring…';
      return;
    }
    const { seconds, deltas } = result;
    const requests = 'hits' in deltas && 'misses' in deltas ? deltas.hits + deltas.misses : null;
    show('requests', requests === null ? '—' : formatRate(requests, seconds), !requests);
    show(
      'hit-ratio',
      requests === null ? '—' : requests === 0 ? 'No request' : `${((deltas.hits / requests) * 100).toFixed(1)}%`,
      !requests
    );
    for (const name of ['puts', 'evictions', 'expirations', 'removals']) {
      show(name, name in deltas ? formatRate(deltas[name], seconds) : '—', !deltas[name]);
    }
    status.textContent = `Over the last ${Math.round(seconds)} s, read every ${RATES_INTERVAL / 1000} s while this page is visible.`;
  };

  const polling = poller(
    async (signal) => {
      const xml = await fetchDocument(rates.dataset.source, 'application/xml', { signal });
      const counters = {};
      // Counters only: an average time (with a unit) can decrease without a reset
      for (const metric of xml.querySelectorAll('cache-info > cache > statistics > metric:not([unit])')) {
        counters[metric.getAttribute('name')] = Number(metric.getAttribute('value'));
      }
      render(samples.add(performance.now(), counters));
    },
    RATES_INTERVAL,
    { onError: () => stop('Unable to read the statistics.') }
  );

  const label = (icon, text) => {
    const svg = document.createElement('svg-icon');
    svg.setAttribute('name', icon);
    button.replaceChildren(svg, ` ${text}`);
  };

  // A new window: rates never span the time reading was stopped
  const start = () => {
    samples = counterWindow(RATES_SPAN);
    render(false);
    polling.start();
    timer = setTimeout(() => stop(`Stopped after ${RATES_DURATION / 60000} minutes.`), RATES_DURATION);
    label('stop', 'Stop');
  };

  const stop = (message) => {
    polling.stop();
    clearTimeout(timer);
    status.textContent = message;
    label('play', 'Start');
  };

  on(rates, 'click', 'button.toggle-rates', () => (polling.running ? stop('Stopped.') : start()));
  rates.hidden = false;
  start();
}
