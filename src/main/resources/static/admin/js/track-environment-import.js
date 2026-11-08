(function () {
    var form = document.querySelector('[data-environment-import]');
    if (!form) return;
    var choices = Array.from(form.querySelectorAll('input[name="selectedTracks"]'));
    var button = form.querySelector('button[type="submit"]');
    var counter = form.querySelector('[data-environment-count]');
    function count() {
        var selected = choices.filter(function (choice) { return choice.checked; }).length;
        counter.textContent = selected + ' layout updates selected';
        button.disabled = selected === 0;
    }
    form.querySelector('[data-environment-tools]').hidden = false;
    form.querySelector('[data-environment-select]').addEventListener('click', function () {
        choices.forEach(function (choice) { choice.checked = true; }); count();
    });
    form.querySelector('[data-environment-clear]').addEventListener('click', function () {
        choices.forEach(function (choice) { choice.checked = false; }); count();
    });
    choices.forEach(function (choice) { choice.addEventListener('change', count); });
    form.addEventListener('submit', function () {
        button.disabled = true;
        form.querySelector('[data-environment-progress]').hidden = false;
    });
    count();
})();
