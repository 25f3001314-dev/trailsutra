(() => {
  const auditButton = document.querySelector('#verifyAudit');
  const auditStatus = document.querySelector('#auditStatus');
  const reportButton = document.querySelector('#reportButton');
  if (auditButton && auditStatus) {
    auditButton.addEventListener('click', async () => {
      auditStatus.textContent = 'Checking audit chain...';
      try {
        const response = await fetch('/api/audit/verify');
        if (!response.ok) throw new Error('Audit check could not be completed.');
        const result = await response.json();
        auditStatus.textContent = result.valid ? 'Audit chain verified' : 'Audit chain mismatch';
      } catch (error) {
        auditStatus.textContent = error.message;
      }
    });
  }
  if (reportButton) {
    reportButton.addEventListener('click', () => {
      window.location.href = reportButton.dataset.reportUrl;
    });
  }
})();
