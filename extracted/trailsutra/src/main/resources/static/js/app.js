(() => {
  const graph = document.querySelector('#trailGraph');
  if (!graph || !window.cytoscape) return;
  const cy = window.cytoscape({
    container: graph,
    elements: [...graphNodes, ...graphEdges],
    layout: {name: 'breadthfirst', directed: true, roots: 'node[kind="victim"]', spacingFactor: 1.2, padding: 20},
    style: [
      {selector: 'node', style: {
        label: 'data(label)', 'text-wrap': 'wrap', 'text-max-width': '105px',
        'font-size': 10, 'text-valign': 'center', 'text-halign': 'center',
        width: 76, height: 54, 'background-color': '#dbeafe', color: '#17324e',
        'border-width': 2, 'border-color': '#8fb8dd'
      }},
      {selector: 'node[kind="victim"]', style: {'background-color': '#1677e8', color: '#fff', 'border-color': '#1677e8'}},
      {selector: 'node[kind="relay"]', style: {'background-color': '#ffe1e4', 'border-color': '#e65c68'}},
      {selector: 'node[kind="aggregator"]', style: {'background-color': '#ffead0', 'border-color': '#f39a24'}},
      {selector: 'node[kind="other"]', style: {'background-color': '#d8f8e3', 'border-color': '#39b56a'}},
      {selector: 'edge', style: {
        'curve-style': 'bezier', 'target-arrow-shape': 'triangle',
        'target-arrow-color': '#5e7892', 'line-color': '#5e7892',
        width: 'data(weight)', label: 'data(label)', 'font-size': 9,
        'text-background-color': '#fff', 'text-background-opacity': 0.9,
        'text-background-padding': 2
      }}
    ]
  });
  cy.on('tap', 'node', event => {
    const selected = event.target;
    const details = document.querySelector('#reasonCard');
    if (details) details.textContent = selected.data('label');
  });
})();
