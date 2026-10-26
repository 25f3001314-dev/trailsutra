(() => {
  const form = document.querySelector('#matchForm');
  const results = document.querySelector('#matchResults');
  const status = document.querySelector('#matchStatus');
  const confirmStatus = document.querySelector('#confirmStatus');
  const csrf = document.querySelector('meta[name="_csrf"]')?.content || '';
  if (!form || !results || !status || !confirmStatus) return;

  const addCell = (row, value) => {
    const cell = document.createElement('td');
    cell.textContent = value ?? '';
    row.appendChild(cell);
  };

  form.addEventListener('submit', async event => {
    event.preventDefault();
    status.textContent = 'Searching transactions...';
    confirmStatus.textContent = '';
    results.replaceChildren();
    const fields = new FormData(form);
    const date = new Date(fields.get('incidentAt'));
    const request = {
      ncrpId: fields.get('ncrpId'),
      victimName: fields.get('victimName'),
      amountPaise: Math.round(Number(fields.get('amount')) * 100),
      incidentAt: date.toISOString(),
      payerBank: fields.get('payerBank'),
      reference: fields.get('reference')
    };
    try {
      const response = await fetch(`/api/cases/${encodeURIComponent(fields.get('caseId'))}/match`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf},
        body: JSON.stringify(request)
      });
      if (!response.ok) throw new Error('Matching request could not be completed.');
      const candidates = await response.json();
      candidates.forEach((candidate, index) => {
        const row = document.createElement('tr');
        addCell(row, String(index + 1));
        addCell(row, candidate.score.toFixed(2));
        addCell(row, candidate.txnId);
        addCell(row, new Date(candidate.postedAt).toLocaleString());
        addCell(row, candidate.sourceName);
        addCell(row, candidate.destinationName);
        const cell = document.createElement('td');
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'mini';
        button.textContent = 'Confirm';
        button.addEventListener('click', async () => {
          confirmStatus.textContent = 'Saving officer confirmation...';
          try {
            const confirmed = await fetch(`/api/cases/${encodeURIComponent(fields.get('caseId'))}/confirm`, {
              method: 'POST',
              headers: {'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf},
              body: JSON.stringify({
                txnId: candidate.txnId,
                score: candidate.score,
                basis: candidate.basis
              })
            });
            if (!confirmed.ok) throw new Error('Confirmation could not be saved.');
            confirmStatus.textContent = 'Transaction confirmed.';
            button.disabled = true;
          } catch (error) {
            confirmStatus.textContent = error.message;
          }
        });
        cell.appendChild(button);
        row.appendChild(cell);
        results.appendChild(row);
      });
      status.textContent = `${candidates.length} matching candidates found.`;
    } catch (error) {
      status.textContent = error.message;
    }
  });
})();
