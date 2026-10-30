import assert from 'node:assert/strict';
import { test } from 'node:test';

import { formatPercent, formatSize } from './format.js';

test('formats sizes in decimal units', () => {
  assert.equal(formatSize(1), '1 bytes');
  assert.equal(formatSize(999), '999 bytes');
  assert.equal(formatSize(25000000), '25 MB');
  assert.equal(formatSize(52428800), '52.4 MB');
  assert.equal(formatSize(4294967296), '4.3 GB');
  assert.equal(formatSize(5e15), '5000 TB');
});

test('formats fractions as percentages', () => {
  assert.equal(formatPercent(0), '0.0%');
  assert.equal(formatPercent(0.021), '2.1%');
  assert.equal(formatPercent(1), '100.0%');
});
