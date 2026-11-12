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
      const { authorized, applied, services, configuration } = await postControl(form);
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
      const failed = [
        configuration === 'reload-failed' && 'the configuration (Berlioz keeps the last valid one)',
        services === 'reload-failed' && 'the services (Berlioz keeps running the last valid ones)'
      ].filter(Boolean);
      if (failed.length) {
        flash(button, `Reload of ${failed.join(' and of ')} failed`, { type: 'error', timeout: 10000 });
      } else if (shown) {
        flash(button, `Reloaded (${applied.join(', ')})`);
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
