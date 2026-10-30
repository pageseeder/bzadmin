import assert from 'node:assert/strict';
import { test } from 'node:test';

import { tokenize } from './xml-tokens.js';

const join = (tokens) => tokens.map(([, text]) => text).join('');

test('splits elements, attributes and text', () => {
  assert.deepEqual(tokenize('<a href="x" b=\'y\'>text</a>'), [
    ['tag', '<a'],
    [null, ' '],
    ['attr', 'href'],
    [null, '='],
    ['value', '"x"'],
    [null, ' '],
    ['attr', 'b'],
    [null, '='],
    ['value', "'y'"],
    ['tag', '>'],
    [null, 'text'],
    ['tag', '</a>']
  ]);
});

test('recognises comments, CDATA sections, declarations and processing instructions', () => {
  assert.deepEqual(tokenize('<?xml version="1.0"?><!DOCTYPE x><!-- <b> --><![CDATA[<c>]]><x/>'), [
    ['pi', '<?xml version="1.0"?><!DOCTYPE x>'],
    ['comment', '<!-- <b> -->'],
    ['cdata', '<![CDATA[<c>]]>'],
    ['tag', '<x/>']
  ]);
});

test('recognises entities in text, not lone ampersands', () => {
  assert.deepEqual(tokenize('a &amp; b &#x3C; & c'), [
    [null, 'a '],
    ['entity', '&amp;'],
    [null, ' b '],
    ['entity', '&#x3C;'],
    [null, ' & c']
  ]);
});

test('keeps prefixed names and XSLT attribute values together', () => {
  const tokens = tokenize('<xsl:value-of select="concat(@a, \'>\')"/>');
  assert.deepEqual(tokens, [
    ['tag', '<xsl:value-of'],
    [null, ' '],
    ['attr', 'select'],
    [null, '='],
    ['value', '"concat(@a, \'>\')"'],
    ['tag', '/>']
  ]);
});

test('runs a construct left open to the end', () => {
  assert.deepEqual(tokenize('<a><!-- truncated <b>'), [
    ['tag', '<a>'],
    ['comment', '<!-- truncated <b>']
  ]);
  assert.deepEqual(tokenize('<a title="trunc'), [
    ['tag', '<a'],
    [null, ' '],
    ['attr', 'title'],
    [null, '='],
    ['value', '"trunc']
  ]);
});

test('keeps every character of malformed sources', () => {
  for (const source of ['< a', '<a <b>', 'x < y > z', '<a b=>', '&', '<!', '<', '</>', '<a\n  b="1"\n/>']) {
    assert.equal(join(tokenize(source)), source, source);
  }
});
