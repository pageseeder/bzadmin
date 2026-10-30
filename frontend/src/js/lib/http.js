/**
 * HTTP helpers replacing `$.get` and `$.post`.
 *
 * Every request has the header `X-Requested-With: XMLHttpRequest`, which the admin filter requires
 * for operations (POST): cross-site pages cannot send it.
 */

import { clearFlash, dim, flash } from './dom.js';
import { RELOAD_HEADER, reloadScopes } from './reload.js';

/**
 * Fetch a URL with the same defaults as jQuery for same-origin requests.
 *
 * @param {string} url
 * @param {RequestInit} [init]
 * @returns {Promise<Response>}
 * @throws {Error} when the response status is not 2xx; the error has a `status` property
 */
export async function request(url, init = {}) {
  const headers = new Headers(init.headers);
  headers.set('X-Requested-With', 'XMLHttpRequest');
  const response = await fetch(url, { credentials: 'same-origin', ...init, headers });
  if (!response.ok) {
    const error = new Error(`${init.method ?? 'GET'} ${url} failed: ${response.status} ${response.statusText}`);
    error.status = response.status;
    throw error;
  }
  return response;
}

/**
 * Fetch and parse a document.
 *
 * The document is parsed by `XMLHttpRequest`, not `DOMParser.parseFromString`: the response of a
 * request is not a Trusted Types sink, so this works under a host policy with
 * `require-trusted-types-for 'script'`. Scripts in the document do not run.
 *
 * @param {string|URL} url
 * @param {DOMParserSupportedType} [type='text/html'] use 'application/xml' for the `.xml` endpoints
 * @param {object} [init]
 * @param {string} [init.method='GET']
 * @param {URLSearchParams} [init.body] posted as `application/x-www-form-urlencoded`
 * @param {AbortSignal} [init.signal] to make the request abortable
 * @returns {Promise<Document>}
 * @throws {Error} when the response status is not 2xx; the error has a `status` property
 */
export function fetchDocument(url, type = 'text/html', init = {}) {
  return load(url, type, init).then((xhr) => xhr.response);
}

/**
 * Fetch a document like `fetchDocument`, with the request to read the response headers.
 *
 * @returns {Promise<XMLHttpRequest>} the request, with the parsed document as `response`
 */
function load(url, type, { method = 'GET', body = null, signal } = {}) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(signal.reason);
      return;
    }
    const xhr = new XMLHttpRequest();
    xhr.open(method, url);
    xhr.responseType = 'document';
    xhr.overrideMimeType(type);
    xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300 && xhr.response) {
        resolve(xhr);
      } else {
        const error = new Error(`${method} ${url} failed: ${xhr.status} ${xhr.statusText}`);
        error.status = xhr.status;
        reject(error);
      }
    });
    xhr.addEventListener('error', () => reject(new TypeError(`${method} ${url} failed`)));
    xhr.addEventListener('abort', () => reject(signal?.reason ?? new DOMException('Aborted', 'AbortError')));
    signal?.addEventListener('abort', () => xhr.abort(), { once: true });
    xhr.send(body);
  });
}

/**
 * Post parameters as `application/x-www-form-urlencoded`, like `$.post`.
 *
 * @param {string} url
 * @param {Record<string, string>|URLSearchParams} [params]
 * @returns {Promise<Response>}
 */
export function post(url, params = {}) {
  return request(url, { method: 'POST', body: new URLSearchParams(params) });
}

/**
 * Post a Berlioz control form (from the `bzadmin-control-form` template) to its overlay service,
 * which returns `<berlioz-control authorized="…">`: the admin filter authorises the control
 * parameter for users who can operate, and Berlioz applies it before the service runs, listing
 * the scopes it applied in the `X-Berlioz-Reload` header.
 *
 * @param {HTMLFormElement} form
 * @returns {Promise<{authorized: boolean, applied: string[], doc: Document}>} whether the user can
 *   operate, the scopes Berlioz applied, and the XML response
 */
export async function postControl(form) {
  const xhr = await load(form.action, 'application/xml', {
    method: 'POST',
    body: new URLSearchParams(new FormData(form))
  });
  const doc = xhr.response;
  return {
    authorized: doc.querySelector('berlioz-control')?.getAttribute('authorized') === 'true',
    applied: reloadScopes(xhr.getResponseHeader(RELOAD_HEADER)),
    doc
  };
}

/**
 * Replace the children of `target` with the children of the element matching `selector`
 * in the page fetched from `url`.
 *
 * @param {string} url
 * @param {string} selector
 * @param {Element} target
 * @returns {Promise<Document>} the fetched document
 * @throws {Error} when no element matches the selector in the fetched document
 */
export async function swapFragment(url, selector, target) {
  const doc = await fetchDocument(url);
  const source = doc.querySelector(selector);
  if (!source) throw new Error(`No element matching '${selector}' in ${url}`);
  target.replaceChildren(...document.adoptNode(source).childNodes);
  return doc;
}

/**
 * Swap a fragment like `swapFragment`, dimming `target` while it loads and showing an error
 * message after `anchor` if it fails.
 *
 * @param {string|URL} url
 * @param {string} selector
 * @param {Element} target
 * @param {object} options
 * @param {Element} options.anchor the error message is inserted after this element
 * @param {string} options.error prefix of the error message, e.g. 'Unable to load log'
 * @returns {Promise<Document|null>} the fetched document, or `null` if it failed
 */
export async function reloadFragment(url, selector, target, { anchor, error: prefix }) {
  dim(target, true);
  try {
    const doc = await swapFragment(url, selector, target);
    clearFlash(anchor);
    return doc;
  } catch (error) {
    flash(anchor, `${prefix}: ${error.message}`, { type: 'error' });
    return null;
  } finally {
    dim(target, false);
  }
}
