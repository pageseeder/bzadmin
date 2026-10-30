import assert from 'node:assert/strict';
import { test } from 'node:test';

import { formatRate, threadDeltas } from './threads.js';

test('computes the CPU share of one processor and the allocation rate', () => {
  const previous = new Map([['1', { cpu: 1e9, allocated: 1e6 }]]);
  const current = new Map([['1', { cpu: 1.5e9, allocated: 3e6 }]]);
  // 0.5 s of CPU and 2 MB over 2 s
  assert.deepEqual(threadDeltas(previous, current, 2000).get('1'), { cpu: 25, allocated: 1e6 });
});

test('leaves the deltas unknown without a previous refresh', () => {
  const current = new Map([['1', { cpu: 1e9, allocated: 1e6 }]]);
  assert.deepEqual(threadDeltas(null, current, 1000).get('1'), { cpu: null, allocated: null });
});

test('leaves the deltas of new threads and missing or lower counters unknown', () => {
  const previous = new Map([
    ['1', { cpu: 2e9 }],
    ['2', { cpu: 1e9, allocated: 5 }]
  ]);
  const current = new Map([
    ['1', { cpu: 1e9 }],
    ['2', { cpu: 1e9 }],
    ['3', { cpu: 1e9, allocated: 5 }]
  ]);
  const deltas = threadDeltas(previous, current, 1000);
  assert.deepEqual(deltas.get('1'), { cpu: null, allocated: null }, 'lower: another thread with the same ID');
  assert.deepEqual(deltas.get('2'), { cpu: 0, allocated: null }, 'no allocation counter now');
  assert.deepEqual(deltas.get('3'), { cpu: null, allocated: null }, 'new thread');
});

test('leaves the deltas unknown without elapsed time', () => {
  const counters = new Map([['1', { cpu: 1e9 }]]);
  assert.deepEqual(threadDeltas(counters, counters, 0).get('1'), { cpu: null, allocated: null });
});

test('formats rates in decimal units', () => {
  assert.equal(formatRate(0), '0 B/s');
  assert.equal(formatRate(999), '999 B/s');
  assert.equal(formatRate(1500), '1.5 KB/s');
  assert.equal(formatRate(2.25e6), '2.3 MB/s');
  assert.equal(formatRate(5e12), '5000.0 GB/s');
});
