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
        if (driverId) {
            hidden.name = 'guest_' + driverId;
            hidden.value = teamValueForRow(row, section);
        } else {
            hidden.name = '';
            hidden.value = '';
        }
    }

    function bindRow(row, section) {
        var input = row.querySelector('.guest-driver-input');
        var subSelect = row.querySelector('.guest-subteam');
        var removeBtn = row.querySelector('.guest-remove');
        if (input) {
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
                row.remove();
            });
        }
    }

    document.querySelectorAll('.guest-section').forEach(function (section) {
        section.querySelectorAll('.guest-row').forEach(function (row) {
            bindRow(row, section);
        });

        var addBtn = section.querySelector('.guest-add-row');
        if (!addBtn) {
            return;
        }
        addBtn.addEventListener('click', function () {
            var templateRow = section.querySelector('.guest-row');
            if (!templateRow) {
                return;
            }
            var clone = templateRow.cloneNode(true);
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
            bindRow(clone, section);
        });
    });
})();
