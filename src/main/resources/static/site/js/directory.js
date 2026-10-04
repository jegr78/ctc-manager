(function () {
    var tools = document.querySelector('.directory-tools');
    if (!tools) return;
    var search = document.getElementById('search-input');
    var season = document.getElementById('season-filter');
    var reset = document.getElementById('directory-reset');
    var count = document.getElementById('directory-count');
    var empty = document.getElementById('directory-empty');
    var rows = document.querySelectorAll('.overview-row');
    function update() {
        var query = search.value.trim().toLocaleLowerCase();
        var visible = 0;
        rows.forEach(function (row) {
            var matchesSeason = !season.value || row.dataset.seasons.split(' ').includes(season.value);
            var matchesSearch = row.dataset.search.toLocaleLowerCase().includes(query);
            row.hidden = !(matchesSeason && matchesSearch);
            if (!row.hidden) visible++;
        });
        count.textContent = visible + ' ' + (visible === 1 ? count.dataset.unit : count.dataset.plural);
        empty.hidden = visible > 0 || (!query && !season.value);
        reset.disabled = !search.value && !season.value;
    }
    search.addEventListener('input', update);
    season.addEventListener('change', update);
    reset.addEventListener('click', function () {
        search.value = '';
        season.value = '';
        update();
        search.focus();
    });
    tools.hidden = false;
    update();
})();
