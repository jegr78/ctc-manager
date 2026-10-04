(function () {
    const source = document.getElementById('archiveModal');
    const trigger = document.querySelector('[data-testid="open-archive-modal"]');
    if (!source || !trigger || typeof HTMLDialogElement === 'undefined') return;
    const dialog = document.createElement('dialog');
    if (typeof dialog.showModal !== 'function') return;
    dialog.id = source.id;
    dialog.className = 'archive-dialog modal-body--md';
    dialog.setAttribute('aria-labelledby', 'archive-title');
    dialog.setAttribute('aria-describedby', 'archive-hint');
    dialog.append(source.querySelector('.archive-dialog-body'));
    source.replaceWith(dialog);
    trigger.hidden = false;
    const cancel = dialog.querySelector('[data-testid="archive-cancel"]');
    cancel.hidden = false;
    trigger.addEventListener('click', function () {
        dialog.showModal();
        const choice = dialog.querySelector('input:checked:not(:disabled)')
            || dialog.querySelector('input[type="radio"]:not(:disabled)') || cancel;
        choice.focus();
    });
    dialog.addEventListener('keydown', function (event) {
        if (event.key !== 'Tab') return;
        const selected = dialog.querySelector('input[type="radio"]:checked')
            || dialog.querySelector('input[type="radio"]:not(:disabled)');
        const controls = Array.from(dialog.querySelectorAll('a[href], button:not(:disabled), input:not(:disabled)'))
            .filter(function (control) {
                return !control.hidden && (control.type !== 'radio' || control === selected);
            });
        const first = controls[0];
        const last = controls[controls.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    });
    cancel.addEventListener('click', function () { dialog.close(); });
    dialog.addEventListener('close', function () { trigger.focus(); });
    dialog.addEventListener('click', function (event) {
        if (event.target !== dialog) return;
        const bounds = dialog.getBoundingClientRect();
        if (event.clientX < bounds.left || event.clientX > bounds.right
                || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
    });
})();
