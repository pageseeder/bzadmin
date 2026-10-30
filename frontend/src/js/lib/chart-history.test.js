import assert from 'node:assert/strict';
import { test } from 'node:test';
import { summarizeHistory } from './chart-history.js';

test('does not invent values for empty or unavailable history', () => {
  for (const values of [[], [null], [NaN, Infinity]]) {
    assert.deepEqual(summarizeHistory(values), { current: null, min: null, max: null });
  }
});

test('preserves spikes and genuine zeros', () => {
  assert.deepEqual(summarizeHistory([10, 90, 0, 20]), { current: 20, min: 0, max: 90 });
});

test('excludes gaps from extrema and keeps missing current values unavailable', () => {
  assert.deepEqual(summarizeHistory([null, 4, null]), { current: null, min: 4, max: 4 });
  assert.deepEqual(summarizeHistory([4, null, 8]), { current: 8, min: 4, max: 8 });
});
