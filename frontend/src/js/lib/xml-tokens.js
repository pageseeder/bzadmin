/**
 * Splits XML source text into tokens for syntax highlighting.
 *
 * Tokens are `[type, text]` pairs whose texts add up to the source: `type` is one of `tag` (the
 * name and the brackets), `attr`, `value` (with its quotes), `comment`, `cdata`, `pi` (processing
 * instructions, the XML declaration and the document type) and `entity`, or `null` for text and
 * spaces. The source does not have to be well-formed: a construct left open at the end (e.g. a
 * truncated source) runs to the end, and stray characters are text.
 */

// Constructs that start with '<', matched at the current position (sticky)
const MARKUP = [
  ['comment', /<!--[^]*?(?:-->|$)/y],
  ['cdata', /<!\[CDATA\[[^]*?(?:\]\]>|$)/y],
  ['pi', /<\?[^]*?(?:\?>|$)/y],
  ['pi', /<![^>]*>?/y]
];

const TAG_START = /<\/?[^\s/>!?<&]+/y;
const TAG_END = /\/?>/y;
const SPACE = /\s+/y;
const ATTR = /[^\s=/>"'<]+/y;
const VALUE = /"[^"]*"?|'[^']*'?/y;
const TEXT = /[^<&]+/y;
const ENTITY = /&(?:#x[\da-f]+|#\d+|[\w.:-]+);/iy;

/**
 * @param {string} source The XML source.
 * @returns {Array<[string|null, string]>} the tokens, adjacent tokens of the same type merged.
 */
export function tokenize(source) {
  const tokens = [];
  let i = 0;

  const push = (type, text) => {
    const last = tokens.at(-1);
    if (last && last[0] === type) last[1] += text;
    else tokens.push([type, text]);
    i += text.length;
  };

  // The text matched by a sticky pattern at the current position, or null
  const match = (pattern) => {
    pattern.lastIndex = i;
    return pattern.exec(source)?.[0] || null;
  };

  // The attributes and the end of a tag whose name has been read
  const tag = () => {
    while (i < source.length) {
      let text;
      if ((text = match(TAG_END))) return push('tag', text);
      if ((text = match(SPACE))) push(null, text);
      else if ((text = match(ATTR))) push('attr', text);
      else if ((text = match(VALUE))) push('value', text);
      else if (source[i] === '<') return;
      else push(null, source[i]);
    }
  };

  outer: while (i < source.length) {
    let text;
    if (source[i] === '<') {
      for (const [type, pattern] of MARKUP) {
        if ((text = match(pattern))) {
          push(type, text);
          continue outer;
        }
      }
      if ((text = match(TAG_START))) {
        push('tag', text);
        tag();
      } else {
        push(null, '<');
      }
    } else if ((text = match(TEXT))) {
      push(null, text);
    } else {
      push(source[i] === '&' && (text = match(ENTITY)) ? 'entity' : null, text || source[i]);
    }
  }
  return tokens;
}
