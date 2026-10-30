import { flash, on } from '../lib/dom.js';
import { postControl, reloadFragment } from '../lib/http.js';

const page = document.querySelector('.page-bzadmin-config');
if (page) {
  const details = page.querySelector('#config-details');

  // When reloading everything (configuration, services, XSLT cache, ETags and redirects)
  on(page, 'submit', '#berlioz-reload', async (event, form) => {
    event.preventDefault();
    if (!confirm('Reload the configuration and the services, and clear the XSLT cache?')) return;
    const button = form.querySelector('button');
    button.disabled = true;
    try {
      const { authorized, applied, doc } = await postControl(form);
      if (!authorized) {
        flash(button, 'Not reloaded: requires the permission to operate', { type: 'error' });
        return;
      }
      if (!applied.includes('config')) {
        flash(button, 'Not reloaded: Berlioz did not apply berlioz-reload', { type: 'error' });
        return;
      }
      // Show the configuration Berlioz now uses, in the current view
      const shown = await reloadFragment(location.href, '#config-details', details, {
        anchor: button,
        error: 'Unable to load configuration'
      });
      if (doc.querySelector('live-services')?.getAttribute('load-status') === 'reload-failed') {
        flash(button, 'Reload of the services failed: Berlioz keeps running the last valid services', {
          type: 'error',
          timeout: 10000
        });
      } else if (shown) {
        // Berlioz does not report a configuration that fails to load: it keeps the last valid one
        // The redirect rules are only reloaded when the host maps the Berlioz redirect filters
        flash(
          button,
          `Reloaded (${applied.join(', ')}): if the configuration has an error, Berlioz keeps the last valid one, see the logs`,
          {
            timeout: 10000
          }
        );
      }
    } catch (error) {
      flash(button, `Unable to reload: ${error.message}`, { type: 'error' });
    } finally {
      button.disabled = false;
    }
  });

  // When switching between the global properties and the deployment
  on(page, 'click', '.secondary-nav a', async (event, link) => {
    event.preventDefault();
    const doc = await reloadFragment(link.href, '#config-details', details, {
      anchor: link.closest('.page-header'),
      error: 'Unable to load configuration'
    });
    if (!doc) return;
    for (const choice of page.querySelectorAll('.secondary-nav a')) {
      choice.classList.toggle('active', choice === link);
      if (choice === link) choice.setAttribute('aria-current', 'page');
      else choice.removeAttribute('aria-current');
    }
    history.replaceState(null, '', link.href);
  });
}
