import assert from 'node:assert/strict';
import { test } from 'node:test';

import { toBerliozPath } from './berlioz-path.js';

test('removes the extension of a path', () => {
  assert.equal(toBerliozPath('/bzadmin/config.html'), '/bzadmin/config');
  assert.equal(toBerliozPath('/bzadmin/config'), '/bzadmin/config');
  assert.equal(toBerliozPath('/v1.2/about'), '/v1.2/about');
  assert.equal(toBerliozPath('/'), '/');
});

test('reduces a full URL to its path', () => {
  assert.equal(toBerliozPath('  https://example.org:8443/about/team.json?x=1#top '), '/about/team');
  assert.equal(toBerliozPath('about/team.html'), '/about/team');
});

test('removes the context path', () => {
  const mapping = { context: '/ctx' };
  assert.equal(toBerliozPath('https://example.org/ctx/about.html', mapping), '/about');
  assert.equal(toBerliozPath('/ctx/about', mapping), '/about');
  assert.equal(toBerliozPath('/ctx', mapping), '/');
  assert.equal(toBerliozPath('/ctxother/about', mapping), '/ctxother/about');
});

test('keeps the extension when Berlioz is mapped by prefix', () => {
  assert.equal(toBerliozPath('/ctx/html/about.html', { context: '/ctx', prefix: '/html' }), '/about.html');
});

test('decodes the path', () => {
  assert.equal(toBerliozPath('/docs/caf%C3%A9.html'), '/docs/café');
  assert.equal(toBerliozPath('/docs/100%25.html'), '/docs/100%');
  assert.equal(toBerliozPath('/docs/%E0%A4%A.html'), '/docs/%E0%A4%A');
});
