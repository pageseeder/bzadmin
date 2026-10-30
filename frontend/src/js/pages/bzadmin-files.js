import { clearFlash, flash } from '../lib/dom.js';
import { fetchDocument } from '../lib/http.js';

const page = document.querySelector('.page-bzadmin-files, .page-bzadmin-files-document');
const tree = page?.querySelector('.directory-tree');
if (tree) {
  const { folders, root, path: current } = tree.dataset;

  // Insert the folders of a folder of the tree
  const insert = async (details) => {
    const url = `${folders}?root=${encodeURIComponent(root)}&path=${encodeURIComponent(details.dataset.path)}`;
    const doc = await fetchDocument(url);
    const list = doc.querySelector('#psml-folders > ul');
    if (!list) throw new Error(`No folders in ${url}`);
    details.append(document.adoptNode(list));
    clearFlash(details.querySelector('summary'));
    details.classList.toggle('empty', !list.children.length);
  };

  // Load each folder once: the same promise for the toggle event and for `reveal`
  const loading = new WeakMap();
  const load = (details) => {
    if (!loading.has(details)) {
      const loaded = insert(details).catch((error) => {
        loading.delete(details);
        flash(details.querySelector('summary'), `Unable to load the folders: ${error.message}`, { type: 'error' });
      });
      loading.set(details, loaded);
    }
    return loading.get(details);
  };

  // The toggle event does not bubble: listen in the capture phase
  tree.addEventListener(
    'toggle',
    (event) => {
      if (event.target instanceof HTMLDetailsElement && event.target.open) load(event.target);
    },
    true
  );

  // Open the folders down to the current one, and select it
  const reveal = async () => {
    const names = current.split('/').filter(Boolean);
    let details = tree.querySelector('details[data-path="/"]');
    for (let i = 0; details; i++) {
      if (i === names.length) {
        details.querySelector(':scope > summary .goto').classList.add('active');
        return;
      }
      details.open = true;
      await load(details);
      const path = `/${names.slice(0, i + 1).join('/')}`;
      details = [...details.querySelectorAll(':scope > ul > li > details')].find(
        (child) => child.dataset.path === path
      );
    }
  };
  reveal();
}
