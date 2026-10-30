import assert from 'node:assert/strict';
import { test } from 'node:test';

import { reloadScopes } from './reload.js';

test('lists the scopes of the header in order', () => {
  assert.deepEqual(reloadScopes('config, services, xslt, etags, redirects'), [
    'config',
    'services',
    'xslt',
    'etags',
    'redirects'
  ]);
  assert.deepEqual(reloadScopes('xslt'), ['xslt']);
  assert.deepEqual(reloadScopes(' services ,etags'), ['services', 'etags']);
});

test('has no scope without the header', () => {
  assert.deepEqual(reloadScopes(null), []);
  assert.deepEqual(reloadScopes(''), []);
  assert.deepEqual(reloadScopes(' , '), []);
});
