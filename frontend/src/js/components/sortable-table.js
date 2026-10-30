/**
 * Makes the table it wraps sortable by clicking on its column headers.
 *
 * <sortable-table sort-column="0" sort-direction="ascending">
 *   <table>…</table>
 * </sortable-table>
 *
 * Sortable headers are the cells of the last row of the `<thead>`; add `data-sort="none"` to a
 * header cell to make that column unsortable. Clicking a header sorts by that column in ascending,
 * then descending order, then restores the original order of the rows.
 *
 * The sort key of a cell is, in order of preference: its `data-sort` attribute, the `datetime`
 * attribute of a `<time>` element it contains, its text content. Keys are compared as numbers
 * when both are numeric (ignoring commas and spaces), otherwise as natural text. Empty keys and
 * dashes (hyphens or en dashes) always sort last.
 */

const ASCENDING = 'ascending';
const DESCENDING = 'descending';

const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });

const NUMBER = /^-?\d+(\.\d+)?$/;

const EMPTY = /^[-\u2013]*$/;

const ICONS = { [ASCENDING]: 'sort-up', [DESCENDING]: 'sort-down' };

function toKey(cell) {
  if (!cell) return '';
  if (cell.dataset.sort !== undefined) return cell.dataset.sort;
  const time = cell.querySelector('time[datetime]');
  if (time) return time.getAttribute('datetime');
  return cell.textContent.trim();
}

function toNumber(key) {
  const value = key.replace(/[,\s]/g, '');
  return NUMBER.test(value) ? Number(value) : NaN;
}

function compareKeys(a, b) {
  const emptyA = EMPTY.test(a);
  const emptyB = EMPTY.test(b);
  if (emptyA || emptyB) return emptyA - emptyB;
  const x = toNumber(a);
  const y = toNumber(b);
  if (!Number.isNaN(x) && !Number.isNaN(y)) return x - y;
  return collator.compare(a, b);
}

class SortableTable extends HTMLElement {
  #state = null;

  /** The rows in their original order, and the `<tbody>` they were taken from. */
  #original = null;

  constructor() {
    super();
    this.addEventListener('click', (event) => {
      const button = event.target instanceof Element ? event.target.closest('.sort-button') : null;
      if (!button || button.closest('sortable-table') !== this) return;
      const column = button.parentElement.cellIndex;
      const direction = this.#state?.column === column ? this.#state.direction : null;
      if (direction === ASCENDING) this.sortBy(column, DESCENDING);
      else if (direction === DESCENDING) this.unsort();
      else this.sortBy(column, ASCENDING);
    });
  }

  connectedCallback() {
    for (const th of this.#headers()) {
      if (th.dataset.sort === 'none' || th.querySelector(':scope > .sort-button')) continue;
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'sort-button';
      button.append(...th.childNodes);
      const icon = document.createElement('svg-icon');
      icon.setAttribute('name', 'sort');
      button.append(icon);
      th.append(button);
    }
    if (!this.#state && this.hasAttribute('sort-column')) {
      const direction = this.getAttribute('sort-direction') === DESCENDING ? DESCENDING : ASCENDING;
      this.sortBy(Number(this.getAttribute('sort-column')), direction);
    }
  }

  /** The current sort as `{column, direction}`, or `null` if the table has not been sorted. */
  get sortState() {
    return this.#state ? { ...this.#state } : null;
  }

  /**
   * Sort the table by the specified column.
   *
   * @param {number} column index of the column
   * @param {'ascending'|'descending'} [direction='ascending']
   */
  sortBy(column, direction = ASCENDING) {
    const table = this.#table();
    const header = this.#headers()[column];
    if (!table || !header || header.dataset.sort === 'none') return;
    this.#state = { column, direction };
    this.#updateHeaders();
    const tbody = table.tBodies[0];
    if (!tbody || tbody.rows.length < 2) return;
    this.#remember(tbody);
    const sign = direction === DESCENDING ? -1 : 1;
    const rows = Array.from(tbody.rows, (row) => ({ row, key: toKey(row.cells[column]) }));
    rows.sort((a, b) => {
      const order = compareKeys(a.key, b.key);
      // Empty keys stay last in both directions
      return EMPTY.test(a.key) || EMPTY.test(b.key) ? order : order * sign;
    });
    tbody.append(...rows.map(({ row }) => row));
  }

  /** Restore the original order of the rows. */
  unsort() {
    this.#state = null;
    this.#updateHeaders();
    const tbody = this.#table()?.tBodies[0];
    if (tbody && this.#original?.tbody === tbody) {
      // Rows removed since remain removed; rows added since go last
      const rows = this.#original.rows.filter((row) => row.parentNode === tbody);
      tbody.prepend(...rows);
    }
    this.#original = null;
  }

  /** Apply the current sort again, for example after the rows have been replaced. */
  resort() {
    if (this.#state) this.sortBy(this.#state.column, this.#state.direction);
  }

  /**
   * Replace the rows of the table with `tbody`, typically taken from a fetched document, and
   * apply the current sort to them.
   *
   * @param {HTMLTableSectionElement} tbody
   */
  replaceRows(tbody) {
    const table = this.#table();
    if (!table) return;
    const rows = document.adoptNode(tbody);
    if (table.tBodies[0]) table.tBodies[0].replaceWith(rows);
    else table.append(rows);
    this.resort();
  }

  /** Keep the order of the rows of `tbody` before it is first sorted. */
  #remember(tbody) {
    if (this.#original?.tbody !== tbody) this.#original = { tbody, rows: Array.from(tbody.rows) };
  }

  #table() {
    return this.querySelector(':scope > table');
  }

  #headers() {
    const rows = this.#table()?.tHead?.rows;
    return rows?.length ? Array.from(rows[rows.length - 1].cells) : [];
  }

  #updateHeaders() {
    this.#headers().forEach((th, index) => {
      const icon = th.querySelector(':scope > .sort-button > svg-icon');
      if (index === this.#state?.column) {
        th.setAttribute('aria-sort', this.#state.direction);
        icon?.setAttribute('name', ICONS[this.#state.direction]);
      } else {
        th.removeAttribute('aria-sort');
        icon?.setAttribute('name', 'sort');
      }
    });
  }
}

export default SortableTable;
