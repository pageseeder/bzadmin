import assert from 'node:assert/strict';
import { test } from 'node:test';

import { adminConfig } from './admin-login.js';

test('adminConfig puts the user name and the hash in an admin element', () => {
  const hash = '$pbkdf2-sha256$i=600000$AAECAwQFBgcICQoLDA0ODw$SdScJfWXhGIJ8Nkud3CrZOHHXpS0zmxQkmXuZxddKh4';
  assert.equal(adminConfig('admin', hash), `<admin username="admin" password="${hash}"/>`);
});

test('adminConfig escapes the user name for an XML attribute', () => {
  assert.equal(adminConfig('a"<b>&c', 'h'), '<admin username="a&quot;&lt;b>&amp;c" password="h"/>');
});
