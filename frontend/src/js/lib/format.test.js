import assert from 'node:assert/strict';
import { test } from 'node:test';

import { formatCount, formatPercent, formatSize } from './format.js';

test('formats sizes in decimal units', () => {
  assert.equal(formatSize(1), '1 bytes');
  assert.equal(formatSize(999), '999 bytes');
  assert.equal(formatSize(25000000), '25 MB');
  assert.equal(formatSize(52428800), '52.4 MB');
  assert.equal(formatSize(4294967296), '4.3 GB');
  assert.equal(formatSize(5e15), '5 PB');
});

test('formats counts with thousands separators', () => {
  assert.equal(formatCount(0), '0');
  assert.equal(formatCount(999), '999');
  assert.equal(formatCount(1234567), '1,234,567');
  assert.equal(formatCount(4294.94, 1), '4,294.9');
  assert.equal(formatCount(2, 1), '2.0');
});

test('formats fractions as percentages', () => {
  assert.equal(formatPercent(0), '0.0%');
  assert.equal(formatPercent(0.021), '2.1%');
  assert.equal(formatPercent(1), '100.0%');
});
