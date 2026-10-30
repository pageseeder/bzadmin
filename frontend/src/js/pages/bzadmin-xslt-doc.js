import { on } from '../lib/dom.js';

const page = document.querySelector('.page-bzadmin-xslt-doc');
if (page) {
  for (const doc of page.querySelectorAll('article')) {
    const nav = doc.querySelector('#xsltdoc-nav nav');
    if (!nav) continue;
    const sections = [...doc.querySelectorAll('section.xsltdoc')];
    const tabs = [...nav.querySelectorAll('a[href^="#"]')];

    const showTarget = (hash, focus = false) => {
      const target = hash ? doc.querySelector(`#${CSS.escape(hash.slice(1))}`) : null;
      const section = target?.closest('section.xsltdoc');
      if (!section) return false;
      for (const candidate of sections) candidate.hidden = candidate !== section;
      for (const tab of tabs) {
        const active = tab.hash === `#${section.id}`;
        tab.parentElement.classList.toggle('active', active);
        if (active) tab.setAttribute('aria-current', 'true');
        else tab.removeAttribute('aria-current');
      }
      if (focus) {
        const destination = target === section ? section.querySelector('h2') : target;
        destination.setAttribute('tabindex', '-1');
        destination.focus();
        destination.scrollIntoView();
      }
      return true;
    };

    // Native fragment links work without script; enhancement reveals their section and
    // moves keyboard focus to the same destination as the visible content.
    on(doc, 'click', 'a[href^="#"]', (event, link) => {
      if (!showTarget(link.hash, true)) return;
      event.preventDefault();
      history.pushState(null, '', link.hash);
    });

    const restore = () => {
      if (!showTarget(location.hash)) showTarget('#xslt-summary');
    };
    window.addEventListener('popstate', restore);
    window.addEventListener('hashchange', restore);
    restore();
  }
}
