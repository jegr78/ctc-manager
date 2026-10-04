(function () {
    const forms = Array.from(document.querySelectorAll('[data-generate-cards]'));
    const status = document.getElementById('card-generation-status');
    if (!forms.length || !status) return;
    const buttons = forms.map(function (form) { return form.querySelector('button[type="submit"]'); });
    const labels = buttons.map(function (button) { return button.textContent; });
    let pending = false;

    function reset() {
        pending = false;
        forms.forEach(function (form) { form.removeAttribute('aria-busy'); });
        buttons.forEach(function (button, index) { button.disabled = false; button.textContent = labels[index]; });
        status.hidden = true;
        status.textContent = '';
    }

    forms.forEach(function (form) {
        form.addEventListener('submit', function (event) {
            if (pending) { event.preventDefault(); return; }
            pending = true;
            form.setAttribute('aria-busy', 'true');
            buttons.forEach(function (button) { button.disabled = true; });
            form.querySelector('button[type="submit"]').textContent = 'Generating...';
            status.textContent = form.dataset.generationMessage;
            status.hidden = false;
        });
    });
    window.addEventListener('pageshow', reset);
})();
