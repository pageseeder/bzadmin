/**
 * The scopes of `berlioz-reload` that Berlioz applied to a request, which it lists in a response
 * header since 0.14.5 (`ReloadScope.HEADER`): `config`, `services`, `xslt`, `etags` by the Berlioz
 * servlet, `redirects` by its redirect filters when the host maps them.
 */
export const RELOAD_HEADER = 'X-Berlioz-Reload';

/**
 * @param {string|null} header the value of the header, e.g. 'config, services'
 * @returns {string[]} the scopes, in the order Berlioz applied them; none without the header
 */
export function reloadScopes(header) {
  return header
    ? header
        .split(',')
        .map((scope) => scope.trim())
        .filter(Boolean)
    : [];
}
