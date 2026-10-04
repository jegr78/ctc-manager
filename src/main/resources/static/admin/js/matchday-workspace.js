(function () {
    var search = document.getElementById('matchday-search');
    var status = document.getElementById('matchday-status');
    var message = document.getElementById('matchday-filter-status');
    if (!search || !status || !message) return;

    var rows = document.querySelectorAll('.match-row[data-teams]');
    function filterMatches() {
        var query = search.value.trim().toLowerCase();
        var count = 0;
        rows.forEach(function (row) {
            row.hidden = !row.dataset.teams.toLowerCase().includes(query)
                || (status.value !== 'all' && row.dataset.resultStatus !== status.value);
            if (!row.hidden) count++;
        });
        message.textContent = count === 0 ? 'No matches meet your filters.'
            : count + (count === 1 ? ' match' : ' matches');
    }
    search.addEventListener('input', filterMatches);
    status.addEventListener('change', filterMatches);
    filterMatches();
})();
