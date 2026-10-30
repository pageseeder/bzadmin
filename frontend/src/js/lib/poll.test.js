import assert from 'node:assert/strict';
import { afterEach, beforeEach, mock, test } from 'node:test';

import { poller } from './poll.js';

// A document whose visibility the tests control
class FakeDocument extends EventTarget {
  hidden = false;
  setHidden(hidden) {
    this.hidden = hidden;
    this.dispatchEvent(new Event('visibilitychange'));
  }
}

let doc;

beforeEach(() => {
  doc = new FakeDocument();
  mock.timers.enable({ apis: ['setTimeout'] });
});

afterEach(() => {
  mock.timers.reset();
});

// Let pending promise callbacks run
const settle = () => new Promise((resolve) => setImmediate(resolve));

test('waits for each call to complete before scheduling the next one', async () => {
  let active = 0;
  let maxActive = 0;
  const pending = [];
  const poll = poller(
    () => {
      active += 1;
      maxActive = Math.max(maxActive, active);
      return new Promise((resolve) => pending.push(() => ((active -= 1), resolve())));
    },
    1000,
    { doc }
  );
  poll.start();
  assert.equal(pending.length, 1);
  mock.timers.tick(5000);
  assert.equal(pending.length, 1, 'No call while the first is in progress');
  pending.shift()();
  await settle();
  mock.timers.tick(999);
  assert.equal(pending.length, 0);
  mock.timers.tick(1);
  assert.equal(pending.length, 1, 'Next call one interval after completion');
  assert.equal(maxActive, 1);
  poll.stop();
});

test('pauses while the page is hidden and resumes when visible', async () => {
  const fn = mock.fn(async () => {});
  const poll = poller(fn, 1000, { doc });
  poll.start();
  await settle();
  assert.equal(fn.mock.callCount(), 1);
  doc.setHidden(true);
  mock.timers.tick(10000);
  await settle();
  assert.equal(fn.mock.callCount(), 1, 'No call while hidden');
  assert.equal(poll.running, true, 'Still running');
  doc.setHidden(false);
  await settle();
  assert.equal(fn.mock.callCount(), 2, 'Resumed at once');
  poll.stop();
});

test('does not start while hidden', async () => {
  const fn = mock.fn(async () => {});
  doc.hidden = true;
  const poll = poller(fn, 1000, { doc });
  poll.start();
  await settle();
  assert.equal(fn.mock.callCount(), 0);
  doc.setHidden(false);
  await settle();
  assert.equal(fn.mock.callCount(), 1);
  poll.stop();
});

test('aborts the call in progress when stopped, and never calls again', async () => {
  let signal;
  const fn = mock.fn((s) => {
    signal = s;
    return new Promise(() => {});
  });
  const onError = mock.fn();
  const poll = poller(fn, 1000, { doc, onError });
  poll.start();
  poll.stop();
  assert.equal(signal.aborted, true);
  mock.timers.tick(10000);
  doc.setHidden(true);
  doc.setHidden(false);
  await settle();
  assert.equal(fn.mock.callCount(), 1);
  assert.equal(onError.mock.callCount(), 0);
});

test('stops and reports the first error', async () => {
  const fn = mock.fn(async () => {
    throw new Error('503');
  });
  const onError = mock.fn();
  const poll = poller(fn, 1000, { doc, onError });
  poll.start();
  await settle();
  assert.equal(poll.running, false);
  assert.equal(onError.mock.callCount(), 1);
  assert.equal(onError.mock.calls[0].arguments[0].message, '503');
  mock.timers.tick(10000);
  await settle();
  assert.equal(fn.mock.callCount(), 1);
});

test('stops after its duration and reports the end', async () => {
  const fn = mock.fn(async () => {});
  const onEnd = mock.fn();
  const poll = poller(fn, 1000, { doc, duration: 5000, onEnd });
  poll.start();
  for (let i = 0; i < 4; i++) {
    await settle();
    mock.timers.tick(1000);
  }
  await settle();
  assert.equal(poll.running, true);
  assert.equal(onEnd.mock.callCount(), 0);
  mock.timers.tick(1000);
  await settle();
  assert.equal(poll.running, false);
  assert.equal(onEnd.mock.callCount(), 1);
  const calls = fn.mock.callCount();
  mock.timers.tick(10000);
  await settle();
  assert.equal(fn.mock.callCount(), calls, 'No call after the end');
});

test('restarts its duration when started again, and never ends once stopped', async () => {
  const onEnd = mock.fn();
  const poll = poller(async () => {}, 1000, { doc, duration: 5000, onEnd });
  poll.start();
  mock.timers.tick(4000);
  poll.stop();
  poll.start();
  mock.timers.tick(4000);
  await settle();
  assert.equal(poll.running, true, 'Duration counted from the second start');
  poll.stop();
  mock.timers.tick(10000);
  assert.equal(onEnd.mock.callCount(), 0);
});
