(function () {
    var datalist = document.getElementById('guestDriverList');
    if (!datalist) {
        return;
    }

    function resolveDriverId(value) {
        var options = datalist.querySelectorAll('option');
        for (var i = 0; i < options.length; i++) {
            if (options[i].value === value) {
                return options[i].getAttribute('data-id');
            }
        }
        return '';
    }

    function teamValueForRow(row, section) {
        var subSelect = row.querySelector('.guest-subteam');
        if (subSelect) {
            return subSelect.value;
        }
        return section.getAttribute('data-team-id') || '';
    }

    function syncRow(row, section) {
        var input = row.querySelector('.guest-driver-input');
        var hidden = row.querySelector('.guest-driver-id');
        if (!input || !hidden) {
            return;
        }
        var driverId = resolveDriverId(input.value);
        input.setCustomValidity(input.value.trim() && !driverId ? 'Choose an existing driver from the suggestions.' : '');
        if (driverId) {
            hidden.name = 'guest_' + driverId;
            hidden.value = teamValueForRow(row, section);
        } else {
            hidden.name = '';
            hidden.value = '';
        }
    }

    function labelRows(section) {
        section.querySelectorAll('.guest-row').forEach(function(row, index) {
            var input = row.querySelector('.guest-driver-input');
            var label = row.querySelector('.guest-driver-field label');
            input.id = 'guest-' + section.dataset.teamId + '-' + index;
            label.htmlFor = input.id;
            label.textContent = 'Guest driver ' + (index + 1);
            row.querySelector('.guest-remove').setAttribute('aria-label', 'Remove guest driver ' + (index + 1));
        });
    }

    function bindRow(row, section) {
        var input = row.querySelector('.guest-driver-input');
        var subSelect = row.querySelector('.guest-subteam');
        var removeBtn = row.querySelector('.guest-remove');
        if (input) {
            input.addEventListener('input', function () { syncRow(row, section); });
            input.addEventListener('change', function () {
                syncRow(row, section);
            });
        }
        if (subSelect) {
            subSelect.addEventListener('change', function () {
                syncRow(row, section);
            });
        }
        if (removeBtn) {
            removeBtn.addEventListener('click', function () {
                var index = Array.from(section.querySelectorAll('.guest-row')).indexOf(row);
                row.remove();
                labelRows(section);
                var remaining = section.querySelectorAll('.guest-row');
                var next = remaining[Math.min(index, remaining.length - 1)];
                (next ? next.querySelector('.guest-driver-input') : section.querySelector('.guest-add-row')).focus();
            });
        }
    }

    document.querySelectorAll('.guest-section').forEach(function (section) {
        var emptyTemplate = section.querySelector('.guest-row').cloneNode(true);
        labelRows(section);
        section.querySelectorAll('.guest-row').forEach(function (row) {
            bindRow(row, section);
        });

        var addBtn = section.querySelector('.guest-add-row');
        if (!addBtn) {
            return;
        }
        addBtn.addEventListener('click', function () {
            var clone = emptyTemplate.cloneNode(true);
            var input = clone.querySelector('.guest-driver-input');
            var hidden = clone.querySelector('.guest-driver-id');
            var subSelect = clone.querySelector('.guest-subteam');
            if (input) {
                input.value = '';
            }
            if (hidden) {
                hidden.name = '';
                hidden.value = '';
            }
            if (subSelect) {
                subSelect.selectedIndex = 0;
            }
            addBtn.parentNode.insertBefore(clone, addBtn);
            labelRows(section);
            bindRow(clone, section);
            input.focus();
        });
    });
})();
