import { clearFlash, flash, on } from '../lib/dom.js';
import { fetchDocument, post, reloadFragment } from '../lib/http.js';

const page = document.querySelector('.page-bzadmin-application');
if (page) {
  const details = page.querySelector('#application-details');
  // Replace the rows, totals, key metrics and start only, so that the sortable table keeps its current sort;
  // the whole table when its columns changed (a status code returned for the first time)
  const reloadStatistics = async (url, button) => {
    const table = page.querySelector('.statistics-table');
    if (!table) return;
    try {
      const doc = await fetchDocument(url);
      const fetched = doc.querySelector('.statistics-table');
      const tbody = fetched?.tBodies[0];
      if (!tbody) throw new Error(`No statistics in ${url}`);
      if (fetched.dataset.columns !== table.dataset.columns) {
        table.closest('.table-scroll').replaceWith(document.adoptNode(fetched.closest('.table-scroll')));
      } else {
        table.closest('sortable-table').replaceRows(tbody);
        // The totals are in the footer, which is not sorted
        if (fetched.tFoot && table.tFoot) table.tFoot.replaceWith(document.adoptNode(fetched.tFoot));
      }
      // The key metrics, and when the statistics started (changed by a reset)
      for (const id of ['#statistics-metrics', '#statistics-since']) {
        const fetchedPart = doc.querySelector(id);
        if (fetchedPart) page.querySelector(id)?.replaceWith(document.adoptNode(fetchedPart));
      }
      clearFlash(button);
    } catch (error) {
      flash(button, `Unable to load statistics: ${error.message}`, { type: 'error' });
    }
  };

  // Refresh statistics table
  on(page, 'click', '#refresh-statistics', (event, button) => {
    reloadStatistics(button.dataset.href, button);
  });

  // Reset statistics (POST), then reload the table
  on(page, 'click', '#reset-statistics', async (event, button) => {
    if (!confirm('Reset all statistics data?')) return;
    try {
      await post(button.dataset.href);
    } catch (error) {
      flash(button, `Unable to reset statistics: ${error.message}`, { type: 'error' });
      return;
    }
    reloadStatistics(page.querySelector('#refresh-statistics').dataset.href, button);
  });

  // When switching between the overview, the performance, the status codes and the libraries
  on(page, 'click', '.secondary-nav a', async (event, link) => {
    event.preventDefault();
    const doc = await reloadFragment(link.href, '#application-details', details, {
      anchor: link.closest('.page-header'),
      error: 'Unable to load application details'
    });
    if (!doc) return;
    for (const choice of page.querySelectorAll('.secondary-nav a')) {
      choice.classList.toggle('active', choice === link);
      if (choice === link) choice.setAttribute('aria-current', 'page');
      else choice.removeAttribute('aria-current');
    }
    history.replaceState(null, '', link.href);
  });

  // Fallback for browsers without Invoker Commands (`commandfor` / `command="show-modal"`)
  if (!('command' in HTMLButtonElement.prototype)) {
    on(page, 'click', 'button[commandfor]', (event, button) => {
      document.getElementById(button.getAttribute('commandfor'))?.showModal();
    });
  }
}
