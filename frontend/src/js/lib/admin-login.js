/**
 * The global configuration of the fallback login with a password hash, as an XML element for
 * `config-<mode>.xml` (the properties `berlioz.admin.username` and `berlioz.admin.password`).
 *
 * @param {string} username
 * @param {string} hash a hash made by `AdminPassword`
 * @returns {string}
 */
export function adminConfig(username, hash) {
  return `<admin username="${escapeAttribute(username)}" password="${escapeAttribute(hash)}"/>`;
}

/**
 * @param {string} value
 * @returns {string} the value escaped for an XML attribute between double quotes
 */
function escapeAttribute(value) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
}
