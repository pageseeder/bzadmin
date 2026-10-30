import { flash, on } from '../lib/dom.js';
import { toBerliozPath } from '../lib/berlioz-path.js';
import { postControl, reloadFragment } from '../lib/http.js';

// The Services page, and the page of the service matching a URL
const page = document.querySelector('.page-bzadmin-services, .page-bzadmin-services-lookup');
if (page) {
  const details = page.querySelector('#services-details');

  // When reloading the services
  on(page, 'submit', '#reload-services', async (event, form) => {
    event.preventDefault();
    const button = form.querySelector('button');
    button.disabled = true;
    try {
      const { authorized, applied, doc } = await postControl(form);
      if (!authorized) {
        flash(button, 'Services not reloaded: requires the permission to operate', { type: 'error' });
        return;
      }
      if (!applied.includes('services')) {
        flash(button, 'Services not reloaded: Berlioz did not apply berlioz-reload', { type: 'error' });
        return;
      }
      // Show the services Berlioz now runs, in the current grouping
      const shown = await reloadFragment(location.href, '#services-details', details, {
        anchor: button,
        error: 'Unable to load services'
      });
      if (doc.querySelector('live-services')?.getAttribute('load-status') === 'reload-failed') {
        flash(button, 'Reload failed: Berlioz keeps running the last valid services', { type: 'error' });
      } else if (shown) {
        flash(button, 'Services reloaded');
      }
    } catch (error) {
      flash(button, `Unable to reload services: ${error.message}`, { type: 'error' });
    } finally {
      button.disabled = false;
    }
  });

  // When looking up the service matching a URL
  on(page, 'submit', '#service-lookup', async (event, form) => {
    event.preventDefault();
    const input = form.elements.url;
    input.value = toBerliozPath(input.value, form.dataset);
    const url = new URL(form.getAttribute('action'), location.href);
    url.search = new URLSearchParams(new FormData(form));
    const doc = await reloadFragment(url, '#service-lookup-result', page.querySelector('#service-lookup-result'), {
      anchor: form,
      error: 'Unable to find the service'
    });
    // The lookup page shows the same view with the result
    if (doc) history.replaceState(null, '', url);
  });

  // When changing the view (grouping or lookup)
  on(page, 'click', '.secondary-nav a', async (event, link) => {
    event.preventDefault();
    const doc = await reloadFragment(link.href, '#services-details', details, {
      anchor: link.closest('.page-header'),
      error: 'Unable to load services'
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
