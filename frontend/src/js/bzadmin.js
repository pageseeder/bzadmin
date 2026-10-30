// Styles
import '../css/bzadmin.css';

import { on } from './lib/dom.js';
import './lib/table-scroll.js';

// Components
import LineChart from './components/line-chart';
import PieChart from './components/pie-chart';
import SortableTable from './components/sortable-table';
import SvgIcon from './components/svg-icon';
import XmlSource from './components/xml-source';

customElements.define('line-chart', LineChart);
customElements.define('pie-chart', PieChart);
customElements.define('sortable-table', SortableTable);
customElements.define('svg-icon', SvgIcon);
customElements.define('xml-source', XmlSource);

// Popovers are positioned under the button that opened them last (see `.info-button` in components.css)
on(document, 'click', '[popovertarget]', (event, button) => {
  document.querySelector('.popover-anchor')?.classList.remove('popover-anchor');
  button.classList.add('popover-anchor');
});

// pages
import './pages/bzadmin-application';
import './pages/bzadmin-cache-details';
import './pages/bzadmin-cache-summary';
import './pages/bzadmin-config';
import './pages/bzadmin-files';
import './pages/bzadmin-logs';
import './pages/bzadmin-services';
import './pages/bzadmin-system';
import './pages/bzadmin-xslt';
import './pages/bzadmin-xslt-doc';
