import assert from 'node:assert/strict';
import { test } from 'node:test';

import { counterWindow, formatRate } from './rates.js';

test('needs two samples', () => {
  const window = counterWindow();
  assert.equal(window.rates(), null);
  window.add(0, { hits: 10 });
  assert.equal(window.rates(), null);
  window.add(5000, { hits: 20 });
  assert.deepEqual(window.rates(), { seconds: 5, deltas: { hits: 10 } });
});

test('slides over the window', () => {
  const window = counterWindow(10000);
  for (let time = 0; time <= 30000; time += 5000) window.add(time, { puts: time / 100 });
  // The most recent sample older than the window is kept: 20 s to 30 s
  assert.deepEqual(window.rates(), { seconds: 10, deltas: { puts: 100 } });
});

test('only compares counters in both samples', () => {
  const window = counterWindow();
  window.add(0, { hits: 1 });
  window.add(1000, { hits: 2, puts: 5 });
  assert.deepEqual(window.rates().deltas, { hits: 1 });
});

test('starts again when the counters are reset', () => {
  const window = counterWindow();
  window.add(0, { hits: 100, puts: 50 });
  assert.equal(window.add(5000, { hits: 120, puts: 60 }), false);
  assert.equal(window.add(10000, { hits: 3, puts: 60 }), true);
  assert.equal(window.rates(), null);
  window.add(15000, { hits: 8, puts: 61 });
  assert.deepEqual(window.rates(), { seconds: 5, deltas: { hits: 5, puts: 1 } });
});

test('formats rates per second or per minute', () => {
  assert.equal(formatRate(0, 60), 'None');
  assert.equal(formatRate(120, 60), '2.0/s');
  assert.equal(formatRate(3, 60), '3.0/min');
});
