(() => {
    const editor = document.querySelector('.import-entry-editor');
    if (!editor) return;
    const season = editor.querySelector('#seasonId');
    const matchday = editor.querySelector('#matchdayId');
    const playoff = editor.querySelector('#playoffMatchupId');
    const panel = editor.querySelector('#newMatchdayPanel');
    const toggle = editor.querySelector('#newMatchdayToggle');
    const label = editor.querySelector('#newMatchdayLabel');
    const hint = editor.querySelector('#newMatchdayHint');
    const error = editor.querySelector('#newMatchdayError');
    const create = editor.querySelector('#newMatchdayCreate');
    const status = editor.querySelector('#import-target-status');
    const retry = editor.querySelector('#retryMatchdays');
    let request;
    let generation = 0;
    let ready = false;
    let loading = false;
    let creating = false;
    let regularStatus = 'Select a season to load its matchdays.';
    const isRegular = () => editor.querySelector('input[name="importType"]:checked').value === 'regular';

    function refresh() {
        const regular = isRegular();
        editor.querySelector('#regularFields').hidden = !regular;
        editor.querySelector('#playoffFields').hidden = regular;
        matchday.disabled = !regular || !ready || loading || !panel.hidden;
        playoff.disabled = regular;
        toggle.disabled = loading || creating;
        create.disabled = creating || !season.value;
        editor.querySelectorAll('button[type="submit"]').forEach(button => {
            button.disabled = regular && (loading || creating);
        });
        status.textContent = regular ? regularStatus : 'Choose a ready playoff matchup from the selected season.';
        retry.hidden = !regular || !retry.dataset.failed;
    }

    function setCreationOpen(open) {
        panel.hidden = !open;
        toggle.textContent = open ? 'Cancel new matchday' : 'New Matchday';
        toggle.setAttribute('aria-expanded', String(open));
        error.hidden = true;
        label.removeAttribute('aria-invalid');
        if (!open) {
            label.value = '';
            hint.hidden = true;
        }
        refresh();
    }

    async function loadMatchdays() {
        request?.abort();
        const current = ++generation;
        const seasonId = season.value;
        ready = false;
        loading = Boolean(seasonId);
        retry.dataset.failed = '';
        matchday.replaceChildren(new Option('-- Select matchday --', ''));
        toggle.hidden = true;
        setCreationOpen(false);
        regularStatus = seasonId ? 'Loading matchdays…' : 'Select a season to load its matchdays.';
        refresh();
        if (!seasonId) return;
        request = new AbortController();
        try {
            const response = await fetch('/admin/matchdays/by-season?seasonId=' + encodeURIComponent(seasonId), {signal: request.signal});
            if (!response.ok) throw new Error('Could not load matchdays');
            const matchdays = await response.json();
            if (current !== generation) return;
            for (const data of matchdays) {
                const option = new Option(data.label, data.id);
                option.dataset.sortIndex = data.sortIndex;
                matchday.appendChild(option);
            }
            ready = matchdays.length > 0;
            toggle.hidden = false;
            regularStatus = ready ? 'Choose an existing matchday, or create a new one.' : 'Create a matchday for this season to continue.';
            if (!ready) {
                setCreationOpen(true);
                hint.hidden = false;
            }
        } catch (failure) {
            if (current !== generation || failure.name === 'AbortError') return;
            regularStatus = 'Could not load matchdays. Retry to continue.';
            retry.dataset.failed = 'true';
        } finally {
            if (current === generation) {
                loading = false;
                refresh();
            }
        }
    }

    function showCreationError(message) {
        error.textContent = message;
        error.hidden = false;
        label.setAttribute('aria-invalid', 'true');
        label.focus();
    }

    create.addEventListener('click', async () => {
        if (creating || !season.value) return;
        const value = label.value.trim();
        if (!value) {
            showCreationError('Label must not be empty');
            return;
        }
        const current = generation;
        const seasonId = season.value;
        creating = true;
        error.hidden = true;
        label.removeAttribute('aria-invalid');
        refresh();
        try {
            const response = await csrfFetch('/admin/matchdays/create-inline', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({seasonId, label: value})
            });
            if (!response.ok) throw new Error(response.status === 409 ? 'Matchday already exists' : 'Could not create matchday. Try again.');
            const data = await response.json();
            if (current !== generation) return;
            const option = new Option(data.label, data.id);
            option.dataset.sortIndex = data.sortIndex;
            matchday.appendChild(option);
            matchday.value = data.id;
            ready = true;
            regularStatus = 'Matchday created and selected: ' + data.label;
            setCreationOpen(false);
            if (isRegular()) matchday.focus();
        } catch (failure) {
            if (current === generation) showCreationError(failure.message);
        } finally {
            creating = false;
            refresh();
        }
    });

    label.addEventListener('keydown', event => {
        if (event.key === 'Enter') {
            event.preventDefault();
            create.click();
        }
    });
    toggle.addEventListener('click', () => {
        setCreationOpen(panel.hidden);
        if (!panel.hidden) label.focus();
    });
    retry.addEventListener('click', loadMatchdays);
    season.addEventListener('change', loadMatchdays);
    editor.querySelectorAll('input[name="importType"]').forEach(radio => radio.addEventListener('change', refresh));
    editor.querySelectorAll('[data-import-source]').forEach(button => button.addEventListener('click', () => {
        const source = button.dataset.importSource;
        const formId = source === 'csv' ? 'csvForm' : 'sheetForm';
        editor.querySelectorAll('[data-import-source]').forEach(other => {
            other.setAttribute('aria-pressed', String(other === button));
        });
        editor.querySelector('#panelCsv').hidden = source !== 'csv';
        const sheet = editor.querySelector('#panelSheet');
        if (sheet) sheet.hidden = source !== 'sheet';
        [season, matchday, playoff, ...editor.querySelectorAll('input[name="importType"]')].forEach(control => {
            control.setAttribute('form', formId);
        });
    }));
    editor.querySelectorAll('form').forEach(form => form.addEventListener('submit', event => {
        if (isRegular() && matchday.disabled) {
            event.preventDefault();
            regularStatus = loading ? 'Wait for the matchdays to load.' : 'Choose or create a matchday before previewing.';
            refresh();
            if (!panel.hidden) label.focus();
            else season.focus();
        }
    }));
    refresh();
})();
