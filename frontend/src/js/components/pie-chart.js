/**
 * Renders a small SVG pie chart from comma-separated values.
 *
 * <pie-chart values="12.5,40,47.5" labels="Used,Free,Unallocated"></pie-chart>
 *
 * Slices start at 12 o'clock and go clockwise. They are filled with `--chart-slice-N` (N from 1)
 * and outlined with `--chart-border`. Each slice has a tooltip with its label, value and
 * percentage.
 */

const SVG = 'http://www.w3.org/2000/svg';

// Slightly smaller than the viewBox so that the border is not clipped
const RADIUS = 0.95;

function toList(value) {
  return value ? value.split(',').map((s) => s.trim()) : [];
}

function point(angle) {
  return `${(RADIUS * Math.cos(angle)).toFixed(4)} ${(RADIUS * Math.sin(angle)).toFixed(4)}`;
}

class PieChart extends HTMLElement {
  static observedAttributes = ['values', 'labels'];

  connectedCallback() {
    this.#render();
  }

  attributeChangedCallback() {
    if (this.isConnected) this.#render();
  }

  #render() {
    const values = toList(this.getAttribute('values')).map((v) => Math.max(Number(v) || 0, 0));
    const labels = toList(this.getAttribute('labels'));
    const total = values.reduce((sum, v) => sum + v, 0);
    const svg = document.createElementNS(SVG, 'svg');
    svg.setAttribute('viewBox', '-1 -1 2 2');
    svg.setAttribute('role', 'img');
    const descriptions = [];
    let angle = -Math.PI / 2;
    values.forEach((value, index) => {
      const pct = total > 0 ? (value * 100) / total : 0;
      const description = `${labels[index] ? `${labels[index]}: ` : ''}${value} (${pct.toFixed(1)}%)`;
      descriptions.push(description);
      if (value === 0) return;
      let slice;
      if (value === total) {
        slice = document.createElementNS(SVG, 'circle');
        slice.setAttribute('r', RADIUS);
      } else {
        const end = angle + (value / total) * 2 * Math.PI;
        const large = end - angle > Math.PI ? 1 : 0;
        slice = document.createElementNS(SVG, 'path');
        slice.setAttribute('d', `M 0 0 L ${point(angle)} A ${RADIUS} ${RADIUS} 0 ${large} 1 ${point(end)} Z`);
        angle = end;
      }
      slice.setAttribute('vector-effect', 'non-scaling-stroke');
      slice.style.fill = `var(--chart-slice-${index + 1})`;
      slice.style.stroke = 'var(--chart-border)';
      const title = document.createElementNS(SVG, 'title');
      title.textContent = description;
      slice.append(title);
      svg.append(slice);
    });
    svg.setAttribute('aria-label', descriptions.join(', '));
    this.replaceChildren(svg);
  }
}

export default PieChart;
