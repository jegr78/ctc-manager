(function () {
    var setup = document.getElementById('season-setup');
    var editDialog = document.getElementById('seasonTeamModal');
    var replaceDialog = document.getElementById('replaceTeamModal');
    if (!setup || !editDialog || !replaceDialog) return;

    document.querySelectorAll('[data-open-season-setup]').forEach(function (link) {
        link.addEventListener('click', function () { setup.open = true; });
    });
    if (window.location.hash === '#season-setup') setup.open = true;

    document.querySelectorAll('.season-team-edit').forEach(function (button) {
        button.addEventListener('click', function () {
            var data = button.dataset;
            editDialog.querySelector('form').reset();
            document.getElementById('modalSeasonTeamId').value = data.id;
            document.getElementById('modalTitle').textContent = 'Edit ' + data.shortname;
            document.getElementById('modalRating').value = data.rating || '';
            ['Primary', 'Secondary', 'Accent'].forEach(function (field) {
                var key = field.toLowerCase();
                document.getElementById('modal' + field).value = data[key] || '';
                document.getElementById('modal' + field + 'Picker').value = data[key] || data['eff' + field];
            });
            document.getElementById('modalLogoPreview').hidden = !data.logo;
            document.getElementById('modalLogoImg').src = data.logo || '';
            editDialog.showModal();
        });
    });

    document.querySelectorAll('.replace-team-btn').forEach(function (button) {
        button.addEventListener('click', function () {
            replaceDialog.querySelector('form').reset();
            document.getElementById('replacePredecessorId').value = button.dataset.teamId;
            document.getElementById('replaceModalTitle').textContent = 'Replace ' + button.dataset.teamName;
            replaceDialog.showModal();
        });
    });
    document.querySelectorAll('[data-dialog-close]').forEach(function (button) {
        button.addEventListener('click', function () { button.closest('dialog').close(); });
    });
    [editDialog, replaceDialog].forEach(function (dialog) {
        dialog.addEventListener('click', function (event) {
            if (event.target !== dialog) return;
            var bounds = dialog.getBoundingClientRect();
            if (event.clientX < bounds.left || event.clientX > bounds.right
                    || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
        });
    });
})();
