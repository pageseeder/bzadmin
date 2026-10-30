/**
 * Renders a live SVG area chart of one or more series, fed with `push()`.
 *
 * <line-chart capacity="100" min="0" max="100" series="2"></line-chart>
 *
 * chart.push(total, used); // one value per series
 *
 * Each series keeps the last `capacity` values, newest on the right. Series are drawn in order,
 * so later series sit on top of earlier ones. Colours come from `--chart-series-N-line` and
 * `--chart-series-N-fill` (N from 1). Missing (`null`) values leave a gap.
 */

import { summarizeHistory } from '../lib/chart-history.js';

const SVG = 'http://www.w3.org/2000/svg';

function toInteger(value, fallback) {
  const n = Number.parseInt(value, 10);
  return Number.isNaN(n) || n < 1 ? fallback : n;
}

function toNumber(value, fallback) {
  const n = value === null ? NaN : Number(value);
  return Number.isNaN(n) ? fallback : n;
}

class LineChart extends HTMLElement {
  static observedAttributes = ['capacity', 'series', 'min', 'max'];

  #data = null;
  #frame = 0;
  #count = 0;

  connectedCallback() {
    this.#render();
  }

  disconnectedCallback() {
    cancelAnimationFrame(this.#frame);
    this.#frame = 0;
  }

  attributeChangedCallback(name) {
    // Changing the shape of the data discards it
    if (name === 'capacity' || name === 'series') {
      this.#data = null;
      this.#count = 0;
    }
    if (this.isConnected) this.#schedule();
  }

  get capacity() {
    return toInteger(this.getAttribute('capacity'), 100);
  }

  /**
   * Append one value per series, dropping the oldest values once the chart is full.
   *
   * @param {...(number|null)} values
   */
  push(...values) {
    this.#series().forEach((buffer, index) => {
      const value = values[index];
      buffer.push(value === null || value === undefined || !Number.isFinite(Number(value)) ? null : Number(value));
      buffer.shift();
    });
    this.#count = Math.min(this.#count + 1, this.capacity);
    this.#schedule();
  }

  /** Clear all values. */
  clear() {
    this.#data = null;
    this.#count = 0;
    this.#schedule();
  }

  #series() {
    if (!this.#data) {
      const count = toInteger(this.getAttribute('series'), 1);
      this.#data = Array.from({ length: count }, () => new Array(this.capacity).fill(null));
    }
    return this.#data;
  }

  #schedule() {
    if (!this.#frame && this.isConnected) {
      this.#frame = requestAnimationFrame(() => {
        this.#frame = 0;
        this.#render();
      });
    }
  }

  #render() {
    const min = toNumber(this.getAttribute('min'), 0);
    const max = toNumber(this.getAttribute('max'), 100);
    const range = max - min || 1;
    const y = (value) => (100 - (Math.min(Math.max(value, min), max) - min) * (100 / range)).toFixed(2);
    const svg = document.createElementNS(SVG, 'svg');
    svg.setAttribute('viewBox', `0 0 ${Math.max(this.capacity - 1, 1)} 100`);
    svg.setAttribute('preserveAspectRatio', 'none');
    svg.setAttribute('aria-hidden', 'true');
    this.#series().forEach((buffer, index) => {
      let area = '';
      let line = '';
      let previous = null;
      buffer.forEach((value, x) => {
        if (value === null) {
          if (previous !== null) area += `L ${previous} 100 Z `;
          previous = null;
          return;
        }
        if (previous === null) {
          area += `M ${x} 100 L ${x} ${y(value)} `;
          line += `M ${x} ${y(value)} `;
        } else {
          area += `L ${x} ${y(value)} `;
          line += `L ${x} ${y(value)} `;
        }
        previous = x;
      });
      if (previous !== null) area += `L ${previous} 100 Z`;
      if (!area) return;
      const fill = document.createElementNS(SVG, 'path');
      fill.setAttribute('d', area.trim());
      fill.style.fill = `var(--chart-series-${index + 1}-fill)`;
      const stroke = document.createElementNS(SVG, 'path');
      stroke.setAttribute('d', line.trim());
      stroke.setAttribute('vector-effect', 'non-scaling-stroke');
      stroke.style.fill = 'none';
      stroke.style.stroke = `var(--chart-series-${index + 1}-line)`;
      svg.append(fill, stroke);
    });
    this.replaceChildren(svg);
    const history = document.getElementById(this.getAttribute('history-target'));
    if (history) history.textContent = `${this.#count} sample${this.#count === 1 ? '' : 's'}`;
    const table = document.getElementById(this.getAttribute('aria-details'));
    this.#series().forEach((buffer, index) => {
      const row = table?.querySelector(`[data-series="${index}"]`);
      if (!row) return;
      const summary = summarizeHistory(this.#count ? buffer.slice(-this.#count) : []);
      for (const name of ['min', 'max']) {
        const cell = row.querySelector(`[data-history="${name}"]`);
        if (cell) cell.textContent = summary[name] === null ? '—' : `${summary[name].toFixed(1)}%`;
      }
    });
  }
}

export default LineChart;
