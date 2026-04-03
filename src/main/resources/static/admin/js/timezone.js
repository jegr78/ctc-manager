/**
 * Timezone conversion: London time <-> local browser timezone.
 *
 * All dateTime values are stored as London time (Europe/London) in the database.
 * This script converts displayed times to the user's local timezone and converts
 * form inputs back to London time before submission.
 */
(function() {
    var LONDON = 'Europe/London';

    /**
     * Convert a London time ISO string (yyyy-MM-ddTHH:mm) to a Date object
     * representing the correct UTC instant.
     */
    function londonToDate(isoLocal) {
        // Create a formatter that tells us the UTC offset for London at this date/time
        var parts = isoLocal.split(/[-T:]/);
        var year = parseInt(parts[0]);
        var month = parseInt(parts[1]) - 1;
        var day = parseInt(parts[2]);
        var hour = parseInt(parts[3]);
        var minute = parseInt(parts[4] || '0');

        // Use Intl to find London's offset at this date
        var tempDate = new Date(year, month, day, hour, minute);
        var londonStr = tempDate.toLocaleString('en-GB', { timeZone: LONDON });
        var utcStr = tempDate.toLocaleString('en-GB', { timeZone: 'UTC' });

        var londonParsed = parseEnGB(londonStr);
        var utcParsed = parseEnGB(utcStr);
        var offsetMs = londonParsed.getTime() - utcParsed.getTime();

        // The actual UTC time: London local time minus London's UTC offset
        return new Date(Date.UTC(year, month, day, hour, minute) - offsetMs);
    }

    /**
     * Parse "dd/MM/yyyy, HH:mm:ss" format from toLocaleString('en-GB').
     */
    function parseEnGB(str) {
        var parts = str.split(/[/,\s:]+/).filter(Boolean);
        return new Date(
            parseInt(parts[2]), parseInt(parts[1]) - 1, parseInt(parts[0]),
            parseInt(parts[3]), parseInt(parts[4]), parseInt(parts[5] || '0')
        );
    }

    /**
     * Convert a Date to the user's local "dd.MM.yyyy HH:mm" format.
     */
    function formatLocal(date) {
        var d = date.getDate().toString().padStart(2, '0');
        var m = (date.getMonth() + 1).toString().padStart(2, '0');
        var y = date.getFullYear();
        var h = date.getHours().toString().padStart(2, '0');
        var min = date.getMinutes().toString().padStart(2, '0');
        return d + '.' + m + '.' + y + ' ' + h + ':' + min;
    }

    /**
     * Convert a Date to datetime-local input value (yyyy-MM-ddTHH:mm).
     */
    function toInputValue(date) {
        var y = date.getFullYear();
        var m = (date.getMonth() + 1).toString().padStart(2, '0');
        var d = date.getDate().toString().padStart(2, '0');
        var h = date.getHours().toString().padStart(2, '0');
        var min = date.getMinutes().toString().padStart(2, '0');
        return y + '-' + m + '-' + d + 'T' + h + ':' + min;
    }

    /**
     * Convert a local Date to London time ISO string (yyyy-MM-ddTHH:mm).
     */
    function dateToLondon(date) {
        var londonStr = date.toLocaleString('en-GB', {
            timeZone: LONDON,
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', hour12: false
        });
        // "dd/MM/yyyy, HH:mm"
        var parts = londonStr.split(/[/,\s:]+/).filter(Boolean);
        return parts[2] + '-' + parts[1] + '-' + parts[0] + 'T' + parts[3] + ':' + parts[4];
    }

    // --- Display conversion: London -> local ---
    document.querySelectorAll('[data-london-time]').forEach(function(el) {
        var iso = el.getAttribute('data-london-time');
        if (!iso) return;
        var date = londonToDate(iso);
        el.textContent = formatLocal(date);
    });

    // --- Input conversion: show local time in datetime-local inputs ---
    document.querySelectorAll('.london-time-input').forEach(function(input) {
        var iso = input.getAttribute('data-london-time');
        if (iso) {
            var date = londonToDate(iso);
            input.value = toInputValue(date);
        }
    });

    // --- Form submission: convert local input back to London time ---
    document.querySelectorAll('form').forEach(function(form) {
        var inputs = form.querySelectorAll('.london-time-input');
        if (inputs.length === 0) return;

        form.addEventListener('submit', function() {
            inputs.forEach(function(input) {
                if (!input.value) return;
                var localDate = new Date(input.value);
                input.value = dateToLondon(localDate);
            });
        });
    });
})();
