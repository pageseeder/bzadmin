import { adminConfig } from '../lib/admin-login.js';
import { clearFlash, flash, on } from '../lib/dom.js';
import { fetchDocument, postControl, reloadFragment } from '../lib/http.js';

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

  // When making a password hash (Login view): the fields are cleared once it is made
  on(page, 'submit', '#password-hash', async (event, form) => {
    event.preventDefault();
    const button = form.querySelector('button[type="submit"]');
    const { password, confirm: again } = form.elements;
    if (password.value !== again.value) {
      flash(button, 'The passwords differ', { type: 'error' });
      again.focus();
      return;
    }
    button.disabled = true;
    try {
      const doc = await fetchDocument(form.action, 'application/xml', {
        method: 'POST',
        body: new URLSearchParams(new FormData(form))
      });
      const hash = doc.querySelector('password-hash')?.getAttribute('hash');
      if (!hash) throw new Error('no hash in the response');
      form.reset();
      clearFlash(button);
      const result = page.querySelector('#password-hash-result');
      result.querySelector('#password-hash-config').textContent = adminConfig(form.dataset.username, hash);
      result.hidden = false;
    } catch (error) {
      flash(button, hashError(error), { type: 'error' });
    } finally {
      button.disabled = false;
    }
  });

  on(page, 'click', '#password-hash-copy', async (event, button) => {
    const config = page.querySelector('#password-hash-config');
    try {
      await navigator.clipboard.writeText(config.textContent);
      flash(button, 'Copied');
    } catch {
      // Select it instead, to copy by hand
      getSelection().selectAllChildren(config);
      flash(button, 'Unable to copy: selected instead', { type: 'error' });
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

/**
 * @param {Error & {status?: number}} error
 * @returns {string} why no hash was made
 */
function hashError(error) {
  switch (error.status) {
    case 400:
      return 'Not hashed: the password is empty, longer than 1024 characters, or typed differently';
    case 403:
      return 'Not hashed: requires the permission to operate';
    case 429:
      return 'Not hashed: the server is making another hash, try again';
    default:
      return `Unable to make the hash: ${error.message}`;
  }
}
