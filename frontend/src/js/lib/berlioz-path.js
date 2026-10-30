/**
 * Reduce a URL to the path Berlioz matches against the URI patterns of its services: no origin,
 * query or fragment, decoded, without the context path and, when Berlioz is mapped by extension
 * (no prefix), without the extension of the last segment.
 *
 * Paths and full URLs are accepted: `https://example.org/ctx/about/team.html?x=1` with the context
 * `/ctx` is `/about/team`.
 *
 * @param {string} url a path or a full URL
 * @param {object} [mapping] as in `header/path` of the Berlioz response
 * @param {string} [mapping.context] the context path, e.g. `/ctx`; empty for the root context
 * @param {string} [mapping.prefix] the servlet path when Berlioz is mapped by prefix, e.g. `/html`
 * @returns {string}
 */
export function toBerliozPath(url, { context = '', prefix = '' } = {}) {
  let path = new URL(url.trim(), 'http://localhost/').pathname;
  try {
    path = decodeURIComponent(path);
  } catch {
    // Keep invalid escape sequences as they are
  }
  for (const start of [context, prefix]) {
    if (start && (path === start || path.startsWith(`${start}/`))) path = path.slice(start.length) || '/';
  }
  return prefix ? path : path.replace(/\.[^./]*$/, '');
}
