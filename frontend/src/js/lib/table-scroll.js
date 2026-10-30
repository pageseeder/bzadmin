/** Keep scroll containers in the tab order only while horizontal scrolling is needed. */
const observed = new WeakSet();
const update = (container) => {
  if (container) container.tabIndex = container.scrollWidth > container.clientWidth + 1 ? 0 : -1;
};
const resize = new ResizeObserver((entries) => {
  for (const { target } of entries) update(target.closest('.table-scroll'));
});

function containers(root) {
  if (!(root instanceof Element || root instanceof Document)) return [];
  return [
    ...(root instanceof Element && root.matches('.table-scroll') ? [root] : []),
    ...root.querySelectorAll('.table-scroll')
  ];
}

function connect(root) {
  for (const container of containers(root)) {
    if (observed.has(container)) continue;
    observed.add(container);
    resize.observe(container);
    // Content can change width without changing the container's width.
    const table = container.querySelector('table');
    if (table) resize.observe(table);
    update(container);
  }
}

connect(document);
new MutationObserver((records) => {
  for (const record of records) {
    for (const root of record.removedNodes) {
      for (const container of containers(root)) {
        if (container.isConnected) continue;
        resize.unobserve(container);
        const table = container.querySelector('table');
        if (table) resize.unobserve(table);
        observed.delete(container);
      }
    }
    for (const root of record.addedNodes) connect(root);
  }
}).observe(document.body, { childList: true, subtree: true });
