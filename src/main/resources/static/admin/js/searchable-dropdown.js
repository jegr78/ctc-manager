(function() {
    document.querySelectorAll('.searchable-dropdown').forEach(function(container) {
        var input = container.querySelector('.dropdown-input');
        var hidden = container.querySelector('input[type="hidden"]');
        var list = container.querySelector('.dropdown-list');
        var items = Array.from(list.querySelectorAll('.dropdown-item'));

        // Pre-select if hidden has value
        var preselected = items.find(function(item) {
            return item.dataset.id === hidden.value;
        });
        if (preselected) {
            input.value = preselected.dataset.label;
        }

        input.addEventListener('focus', function() {
            list.style.display = 'block';
        });

        input.addEventListener('input', function() {
            var q = this.value.toLowerCase();
            items.forEach(function(item) {
                var match = item.dataset.label.toLowerCase().includes(q)
                         || item.textContent.toLowerCase().includes(q);
                item.style.display = match ? '' : 'none';
            });
            list.style.display = 'block';
            hidden.value = '';
        });

        items.forEach(function(item) {
            item.addEventListener('click', function() {
                if (this.classList.contains('disabled')) return;
                input.value = this.dataset.label;
                hidden.value = this.dataset.id;
                list.style.display = 'none';
            });
        });

        document.addEventListener('click', function(e) {
            if (!container.contains(e.target)) {
                list.style.display = 'none';
            }
        });
    });

    // Dynamic update when home team changes
    var homeSelect = document.getElementById('homeTeamId');
    var matchdaySelect = document.getElementById('matchdayId');
    if (homeSelect && matchdaySelect) {
        homeSelect.addEventListener('change', updateUsedSelections);
    }

    function updateUsedSelections() {
        var homeTeamId = homeSelect ? homeSelect.value : '';
        var matchdayOption = matchdaySelect ? matchdaySelect.selectedOptions[0] : null;
        var seasonId = matchdayOption ? matchdayOption.dataset.seasonId : '';
        if (!homeTeamId || !seasonId) return;

        var raceIdInput = document.querySelector('input[name="id"]');
        var excludeRaceId = raceIdInput ? raceIdInput.value : '';
        var url = '/admin/races/used-selections?seasonId=' + seasonId
                + '&homeTeamId=' + homeTeamId;
        if (excludeRaceId) url += '&excludeRaceId=' + excludeRaceId;

        fetch(url)
            .then(function(r) { return r.json(); })
            .then(function(data) {
                document.querySelectorAll('#carList .dropdown-item').forEach(function(item) {
                    item.classList.toggle('disabled',
                        data.usedCarIds && data.usedCarIds.indexOf(item.dataset.id) !== -1);
                });
                document.querySelectorAll('#trackList .dropdown-item').forEach(function(item) {
                    item.classList.toggle('disabled',
                        data.usedTrackIds && data.usedTrackIds.indexOf(item.dataset.id) !== -1);
                });
            });
    }
})();
