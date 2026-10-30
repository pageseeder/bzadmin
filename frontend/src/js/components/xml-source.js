/**
 * Highlights the XML source in the `<code>` element it contains.
 *
 * <xml-source><pre><code>&lt;document level="portable"&gt;…</code></pre></xml-source>
 *
 * The text is replaced by spans with the classes `xml-tag`, `xml-attr`, `xml-value`,
 * `xml-comment`, `xml-cdata`, `xml-pi` and `xml-entity`, built with `textContent` (no markup is
 * parsed). Sources longer than `MAX_LENGTH` characters are left as text.
 */

import { tokenize } from '../lib/xml-tokens.js';

const MAX_LENGTH = 512 * 1024;

class XmlSource extends HTMLElement {
  connectedCallback() {
    const code = this.querySelector('code');
    if (!code || this.hasAttribute('highlighted')) return;
    const source = code.textContent;
    if (source.length > MAX_LENGTH) return;
    const fragment = document.createDocumentFragment();
    for (const [type, text] of tokenize(source)) {
      if (type) {
        const span = document.createElement('span');
        span.className = `xml-${type}`;
        span.textContent = text;
        fragment.append(span);
      } else {
        fragment.append(text);
      }
    }
    code.replaceChildren(fragment);
    this.setAttribute('highlighted', '');
  }
}

export default XmlSource;
