(() => {
  const form = document.querySelector('#reviewForm');
  const status = document.querySelector('#reviewStatus');
  const csrf = document.querySelector('meta[name="_csrf"]')?.content || '';
  if (!form || !status) return;
  const money = paise => '₹' + (paise / 100).toLocaleString('en-IN', {maximumFractionDigits: 2});

  document.querySelectorAll('[data-decision]').forEach(button => {
    button.addEventListener('click', async () => {
      if (!form.reportValidity()) return;
      const fields = new FormData(form);
      const payload = {
        caseId: fields.get('caseId'),
        token: fields.get('token'),
        decision: button.dataset.decision,
        reason: fields.get('reason'),
        lienPaise: Math.round(Number(fields.get('lien')) * 100)
      };
      status.textContent = 'Saving review...';
      try {
        const response = await fetch('/api/reviews', {
          method: 'POST',
          headers: {'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf},
          body: JSON.stringify(payload)
        });
        if (!response.ok) throw new Error('Review could not be saved.');
        const result = await response.json();
        status.textContent = `Review saved · lien ${money(result.lienPaise)}`;
      } catch (error) {
        status.textContent = error.message;
      }
    });
  });
})();
