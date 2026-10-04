(function () {
    var preview = document.querySelector('[data-sync-preview]');
    var form = document.querySelector('[data-sync-form]');
    if (!form) return;
    var submit = form.querySelector('button[type="submit"]');
    var progress = form.querySelector('[data-sync-progress]');

    function updateSelection() {
        var cars = preview.querySelectorAll('[name="selectedCars"]:checked').length;
        var tracks = preview.querySelectorAll('[name="selectedTracks"]:checked').length;
        preview.querySelector('[data-sync-selection]').textContent = cars + ' cars and ' + tracks + ' tracks selected';
        submit.disabled = cars + tracks === 0;
    }
    if (preview) {
        preview.querySelectorAll('[data-sync-select], [data-sync-clear]').forEach(function (button) {
            var name = button.dataset.syncSelect || button.dataset.syncClear;
            var checks = Array.from(preview.querySelectorAll('input[name="' + name + '"]'));
            button.disabled = checks.length === 0;
            button.addEventListener('click', function () {
                checks.forEach(function (check) { check.checked = Boolean(button.dataset.syncSelect); });
                updateSelection();
            });
        });
        preview.addEventListener('change', function (event) {
            if (event.target.matches('input[type="checkbox"]')) updateSelection();
        });
        preview.querySelectorAll('[data-sync-tools]').forEach(function (tools) { tools.hidden = false; });
        updateSelection();
    }
    form.addEventListener('submit', function (event) {
        if (preview && preview.querySelectorAll('input[type="checkbox"]:checked').length === 0) {
            event.preventDefault();
            return;
        }
        form.setAttribute('aria-busy', 'true');
        submit.disabled = true;
        submit.textContent = form.dataset.syncAction === 'fetch' ? 'Fetching...' : 'Importing...';
        progress.hidden = false;
    });
})();
