import { flash, on } from '../lib/dom.js';
import { post } from '../lib/http.js';

const page = document.querySelector('.page-bzadmin-cache-summary');
const section = page?.querySelector('section.cache-overview');
if (section) {
  // Clear one cache, named by provider, manager and name
  on(section, 'click', 'button.clear-cache', async (event, button) => {
    const { provider, manager, cache, scope } = button.dataset;
    const where = scope === 'local' ? '' : ` Its scope is ${scope}: this may affect other nodes.`;
    if (!confirm(`Are you sure you want to remove every entry of the cache ${cache}?${where}`)) return;
    try {
      await post(section.dataset.clear, { provider, manager, cache });
      window.location.reload();
    } catch {
      flash(button.closest('table'), 'Unable to clear cache', { type: 'error' });
    }
  });

  on(section, 'click', '#clear-all', async (event, button) => {
    if (!confirm('Are you sure you want to remove every entry of ALL caches?')) return;
    try {
      await post(section.dataset.clear);
      window.location.reload();
    } catch {
      flash(button, 'Unable to clear caches', { type: 'error' });
    }
  });
}
