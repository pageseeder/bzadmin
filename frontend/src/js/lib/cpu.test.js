import assert from 'node:assert/strict';
import { test } from 'node:test';

import { cpuSample, cpuUsage, formatDuration, formatShortDuration } from './cpu.js';

const sample = (uptime, processTime, gcTime, systemLoad = 0.5) => ({
  processors: 4,
  uptime,
  processTime,
  gcTime,
  systemLoad
});

test('reads the attributes it has', () => {
  const attributes = { processors: '8', 'uptime-ms': '1000', 'system-load': '0.25', 'load-average': '1.5' };
  const element = {
    hasAttribute: (name) => name in attributes,
    getAttribute: (name) => attributes[name] ?? null
  };
  assert.deepEqual(cpuSample(element), {
    processors: 8,
    uptime: 1000,
    processTime: undefined,
    gcTime: undefined,
    systemLoad: 0.25,
    loadAverage: 1.5
  });
});

test('computes the usage between two samples', () => {
  // 2 s of CPU over 1 s on 4 processors; 100 ms of GC over 1 s
  const usage = cpuUsage(sample(1000, 5e9, 100), sample(2000, 7e9, 200));
  assert.deepEqual(usage, { machine: 50, process: 50, gc: 10 });
});

test('only shows the machine for the first sample', () => {
  assert.deepEqual(cpuUsage(null, sample(1000, 5e9, 100)), { machine: 50, process: null, gc: null });
});

test('does not compare samples too far apart or out of order', () => {
  assert.equal(cpuUsage(sample(1000, 0, 0), sample(7000, 1e9, 10)).process, null);
  assert.equal(cpuUsage(sample(2000, 0, 0), sample(1000, 1e9, 10)).process, null);
  assert.equal(cpuUsage(sample(1000, 0, 0), sample(1000, 1e9, 10)).process, null);
});

test('leaves unavailable or decreasing counters out', () => {
  const usage = cpuUsage(sample(1000, undefined, 100, null), sample(2000, undefined, 50, null));
  assert.deepEqual(usage, { machine: null, process: null, gc: null });
});

test('caps the usage at 100%', () => {
  const usage = cpuUsage(sample(1000, 0, 0), sample(2000, 9e9, 2000));
  assert.equal(usage.process, 100);
  assert.equal(usage.gc, 100);
});

test('formats durations as the page does', () => {
  assert.equal(formatDuration(999), '0s');
  assert.equal(formatDuration(5230), '5s');
  assert.equal(formatDuration(3600000), '1h 0min 0s');
  assert.equal(formatDuration(90061000), '1d 1h 1min 1s');
});

test('formats durations with their two largest units', () => {
  assert.equal(formatShortDuration(999), '0s');
  assert.equal(formatShortDuration(65000), '1min 5s');
  assert.equal(formatShortDuration(3600000), '1h 0min');
  assert.equal(formatShortDuration(90061000), '1d 1h');
});
