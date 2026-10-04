(() => {
    const form = document.getElementById('import-execute-form');
    if (!form) return;
    form.addEventListener('change', event => {
        const select = event.target;
        if (!(select instanceof HTMLSelectElement) || !select.name.startsWith('confirm_')) return;
        for (const other of form.querySelectorAll('select')) {
            if (other.name === select.name) other.value = select.value;
        }
    });
})();
