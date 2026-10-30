import { flash, on } from '../lib/dom.js';
import { postControl } from '../lib/http.js';

const page = document.querySelector('.page-bzadmin-xslt');
if (page) {
  // When clearing the XSLT cache
  on(page, 'submit', '#clear-xslt-cache', async (event, form) => {
    event.preventDefault();
    const button = form.querySelector('button');
    button.disabled = true;
    try {
      const { authorized, applied } = await postControl(form);
      if (!authorized) {
        flash(button, 'XSLT cache not cleared: requires the permission to operate', { type: 'error' });
      } else if (!applied.includes('xslt')) {
        flash(button, 'XSLT cache not cleared: Berlioz did not apply berlioz-reload', { type: 'error' });
      } else {
        flash(button, 'XSLT cache cleared');
      }
    } catch (error) {
      flash(button, `Unable to clear XSLT cache: ${error.message}`, { type: 'error' });
    } finally {
      button.disabled = false;
    }
  });
}
