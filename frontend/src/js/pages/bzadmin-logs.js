import { clearFlash, dim, flash, on } from '../lib/dom.js';
import { fetchDocument, post, reloadFragment } from '../lib/http.js';

/** The maximum number of lines of a raw log kept in the page when new lines are added. */
const MAX_LINES = 5000;

// The Recent and Files views, and a log file on its own page
const page = document.querySelector('.page-bzadmin-logs, .page-bzadmin-logs-raw');
if (page) {
  /** Shows a log file in place of the list of files or of the log file shown. */
  const showLog = (form, id) => {
    form.elements.id.value = id;
    reloadFragment(`${form.action}?id=${encodeURIComponent(id)}`, '#log-content', page.querySelector('#log-content'), {
      anchor: form.querySelector('[type=submit]'),
      error: 'Unable to load log'
    });
  };

  // When selecting a log file to view
  on(page, 'submit', '#switch-logs', (event, form) => {
    event.preventDefault();
    showLog(form, form.elements.id.value);
  });

  // When opening a log file from the list of files (the link opens its page without script)
  on(page, 'click', '.log-file-link', (event, link) => {
    const form = page.querySelector('#switch-logs');
    if (!form || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey) return;
    event.preventDefault();
    showLog(form, link.dataset.id);
  });

  /** Removes a line of a raw log: a span followed by its line feed. */
  const removeLine = (line) => {
    if (line.nextSibling?.nodeType === Node.TEXT_NODE) line.nextSibling.remove();
    line.remove();
  };

  /** A cursor of the file (`identity.position.line`, see LogCursor) moved to the start of a line. */
  const cursorAt = (cursor, line) => cursor.replace(/\.\d+\.\d+$/, `.${line.dataset.position}.${line.dataset.n ?? 0}`);

  /**
   * Fetches lines of a raw log for a form of the log shown.
   *
   * @returns the fetched lines, or null when the file was replaced and is shown again from its end.
   */
  const fetchLines = async (form) => {
    const content = page.querySelector('#log-content');
    const doc = await fetchDocument(`${form.action}?${new URLSearchParams(new FormData(form))}`);
    const fetched = doc.querySelector('#log-content');
    const log = fetched?.querySelector('pre.rawlog');
    if (!log || !fetched.querySelector('#follow-log')) throw new Error('No log in the response');
    // The file was rotated or truncated: show it again from its end
    if (log.dataset.reset === 'true') {
      content.replaceChildren(...document.adoptNode(fetched).childNodes);
      flash(content.querySelector('#follow-log [type=submit]'), 'The log file was replaced: showing its end');
      return null;
    }
    return fetched;
  };

  /** Counts the complete lines in a list of line spans. */
  const describe = (lines, kind) => {
    const count = lines.filter((line) => !line.dataset.incomplete).length;
    return count === 0 ? `No ${kind} lines` : `${count} ${kind} line${count === 1 ? '' : 's'}`;
  };

  // When showing the lines added to a raw log since it was loaded (the forms are part of the swapped content)
  on(page, 'submit', '#follow-log', async (event, form) => {
    event.preventDefault();
    const content = page.querySelector('#log-content');
    const button = form.querySelector('[type=submit]');
    dim(content, true);
    try {
      const fetched = await fetchLines(form);
      if (!fetched) return;
      const code = content.querySelector('pre.rawlog code');
      const added = [...fetched.querySelectorAll('pre.rawlog > code > span')];
      // The last line had no line feed yet: it is sent again
      const incomplete = code.lastElementChild;
      if (incomplete?.dataset.incomplete) removeLine(incomplete);
      code.append(...document.adoptNode(fetched.querySelector('pre.rawlog code')).childNodes);
      // Drop the oldest lines: the earlier lines are then read from the first line kept
      if (code.children.length > MAX_LINES) {
        for (let count = code.children.length; count > MAX_LINES; count--) removeLine(code.firstElementChild);
        const earlier = content.querySelector('#earlier-log');
        earlier.elements.before.value = cursorAt(form.elements.cursor.value, code.firstElementChild);
        earlier.hidden = false;
      }
      form.elements.cursor.value = fetched.querySelector('#follow-log').elements.cursor.value;
      // Keep the button in view, under the new lines
      button.scrollIntoView({ block: 'nearest' });
      flash(button, describe(added, 'new'));
    } catch (error) {
      flash(button, `Unable to load new lines: ${error.message}`, { type: 'error' });
    } finally {
      dim(content, false);
    }
  });

  // When showing the lines of a raw log before the first one shown
  on(page, 'submit', '#earlier-log', async (event, form) => {
    event.preventDefault();
    const content = page.querySelector('#log-content');
    // The messages go after the text, so that the button stays in place
    const text = form.querySelector('p > span');
    dim(content, true);
    try {
      const fetched = await fetchLines(form);
      if (!fetched) return;
      const code = content.querySelector('pre.rawlog code');
      const added = [...fetched.querySelectorAll('pre.rawlog > code > span')];
      code.prepend(...document.adoptNode(fetched.querySelector('pre.rawlog code')).childNodes);
      // Drop the newest lines: the new lines are then read from the first line dropped
      if (code.children.length > MAX_LINES) {
        const follow = content.querySelector('#follow-log');
        let line;
        for (let count = code.children.length; count > MAX_LINES; count--) {
          line = code.lastElementChild;
          removeLine(line);
        }
        follow.elements.cursor.value = cursorAt(follow.elements.cursor.value, line);
      }
      const before = fetched.querySelector('#earlier-log').elements.before.value;
      form.elements.before.value = before;
      // The first line of the file is shown
      clearFlash(text);
      if (!before) form.hidden = true;
      flash(before ? text : content.querySelector('pre.rawlog'), describe(added, 'earlier'));
    } catch (error) {
      flash(text, `Unable to load earlier lines: ${error.message}`, { type: 'error' });
    } finally {
      dim(content, false);
    }
  });

  // When changing the capture threshold of the recent events (the form is part of the swapped content);
  // the levels of the application's loggers do not change
  on(page, 'submit', '#set-threshold', async (event, form) => {
    event.preventDefault();
    const threshold = form.elements.threshold.value;
    try {
      await post(form.action, { threshold });
      flash(form, `Capture threshold changed to ${threshold}`);
    } catch (error) {
      flash(form, `Unable to change the capture threshold to ${threshold}: ${error.message}`, { type: 'error' });
    }
  });
}
