/**
 * DOM helpers replacing the jQuery utilities used by the page modules.
 */

const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
const messages = new WeakMap();

/** Clear the previous message for an operation once it has been resolved. */
export function clearFlash(anchor) {
  const message = messages.get(anchor);
  if (!message) return;
  if (message.contains(document.activeElement)) {
    const target = anchor.matches('button, input, select, textarea, a[href], summary, [tabindex]')
      ? anchor
      : anchor.querySelector('button, input, select, textarea, a[href], summary, [tabindex]');
    if (target) target.focus();
    else {
      const tabindex = anchor.getAttribute('tabindex');
      anchor.setAttribute('tabindex', '-1');
      anchor.focus();
      if (tabindex === null) anchor.removeAttribute('tabindex');
      else anchor.setAttribute('tabindex', tabindex);
    }
  }
  message.remove();
  messages.delete(anchor);
}

/**
 * Add a delegated event listener.
 *
 * The handler is called with the event and the element matching the selector, only when
 * that element is inside `root`, so listeners keep working after the content is replaced.
 *
 * @param {Element|Document} root
 * @param {string} type
 * @param {string} selector
 * @param {(event: Event, element: Element) => void} handler
 */
export function on(root, type, selector, handler) {
  root.addEventListener(type, (event) => {
    const element = event.target instanceof Element ? event.target.closest(selector) : null;
    if (element && root.contains(element)) {
      handler(event, element);
    }
  });
}

/**
 * Fade an element out.
 *
 * @param {Element} element
 * @param {number} [duration=400] in milliseconds
 * @returns {Promise<void>} resolved when the animation is complete
 */
export async function fadeOut(element, duration = 400) {
  if (reducedMotion.matches) return;
  await element.animate([{ opacity: 1 }, { opacity: 0 }], { duration, fill: 'forwards' }).finished;
}

/**
 * Dim an element while its content is loading.
 *
 * @param {Element} element
 * @param {boolean} dimmed
 */
export function dim(element, dimmed) {
  element.classList.toggle('is-loading', dimmed);
  element.setAttribute('aria-busy', String(dimmed));
}

/**
 * Display a message after an element, replacing its previous message. Errors stay until
 * dismissed or cleared; successful status messages fade out after the timeout.
 *
 * @param {Element} anchor the message is inserted after this element
 * @param {string} text
 * @param {object} [options]
 * @param {'ok'|'error'} [options.type='ok']
 * @param {number} [options.timeout=3000] milliseconds before a success message fades out
 * @returns {HTMLElement} the message element
 */
export function flash(anchor, text, { type = 'ok', timeout = 3000 } = {}) {
  clearFlash(anchor);
  const message = document.createElement('span');
  message.className = `message ${type}`;
  message.setAttribute('role', type === 'error' ? 'alert' : 'status');
  const icon = document.createElement('svg-icon');
  icon.setAttribute('name', type);
  message.append(icon, ` ${text}`);
  if (type === 'error') {
    const dismiss = document.createElement('button');
    dismiss.type = 'button';
    dismiss.className = 'button small hollow dismiss-message';
    dismiss.textContent = 'Dismiss';
    dismiss.setAttribute('aria-label', 'Dismiss error');
    dismiss.addEventListener('click', () => clearFlash(anchor));
    message.append(' ', dismiss);
  }
  anchor.after(message);
  messages.set(anchor, message);
  if (type !== 'error') {
    setTimeout(async () => {
      await fadeOut(message, 600);
      message.remove();
      if (messages.get(anchor) === message) messages.delete(anchor);
    }, timeout);
  }
  return message;
}
