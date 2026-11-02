(() => {
    const editor = document.querySelector('.race-setup-editor');
    if (!editor) return;
    const home = editor.querySelector('#homeTeamId');
    const matchday = editor.querySelector('#matchdayId');
    const entry = editor.querySelector('#carPoolEntryId');
    const car = editor.querySelector('#carId');
    const track = editor.querySelector('#trackId');
    const categoryField = editor.querySelector('[data-category-pool-field]');
    const hint = editor.querySelector('[data-car-choice-hint]');
    const status = editor.querySelector('[data-used-selection-status]');
    const entryDefault = editor.querySelector('[data-entry-default]');
    const carDefault = editor.querySelector('[data-car-default]');
    const trackDefault = editor.querySelector('[data-track-default]');
    let category = !entry.disabled;
    let renderedSeason = matchday.selectedOptions[0]?.dataset.seasonId;
    let request;
    let generation = 0;

    function syncDefaults() {
        entryDefault.value = entry.value;
        entryDefault.disabled = !category;
        carDefault.value = car.value;
        trackDefault.value = track.value;
    }

    function replaceOptions(select, choices, selected, placeholder, preserveUnknown) {
        const oldLabel = select.selectedOptions[0]?.textContent || 'Unavailable submitted choice';
        select.replaceChildren(new Option(placeholder, ''));
        choices.forEach(choice => {
            const option = new Option(choice.label, choice.id);
            option.disabled = Boolean(choice.disabled);
            select.add(option);
        });
        const exists = choices.some(choice => choice.id === selected);
        if (selected && !exists && preserveUnknown) select.add(new Option(oldLabel, selected));
        select.value = exists || preserveUnknown ? selected : '';
    }

    async function refresh(reason) {
        const ticket = ++generation;
        if (request) request.abort();
        const season = matchday.selectedOptions[0]?.dataset.seasonId;
        if (!season) {
            status.textContent = 'Choose a matchday to load available selections.';
            return;
        }
        request = new AbortController();
        const selectedEntry = entry.value;
        const params = new URLSearchParams({seasonId: season});
        if (home.value) params.set('homeTeamId', home.value);
        const raceId = editor.querySelector('input[name="id"]').value;
        if (raceId) params.set('excludeRaceId', raceId);
        status.textContent = 'Checking available entries, cars and tracks…';
        let data;
        let failed = false;
        try {
            const response = await fetch('/admin/races/used-selections?' + params, {signal: request.signal});
            if (!response.ok) throw new Error('Selection check failed');
            data = await response.json();
        } catch {
            failed = true;
        }
        // A response body can finish after cancellation; only the latest input may update the editor.
        if (ticket !== generation) return;
        if (failed) {
            status.textContent = 'Could not refresh choices. Use Refresh choices to try again.';
            return;
        }
        category = data.carPoolMode === 'CATEGORY_POOL';
        const frozen = data.carPoolState === 'FROZEN';
        const entries = data.entries || [];
        const sameSeason = season === renderedSeason;
        categoryField.hidden = !category;
        entry.disabled = !category;
        replaceOptions(entry, entries.map(choice => ({
            id: choice.id,
            label: choice.label + (choice.consumed ? ' (already used)' : !frozen ? ' (not frozen)' : ''),
            disabled: choice.consumed || !frozen
        })), selectedEntry, 'Select pool entry', sameSeason);
        if (!category) entry.value = '';
        const selected = entries.find(choice => choice.id === entry.value);
        const usedCars = data.usedCarIds || [];
        const carChoices = category ? selected?.cars || [] : data.fixedCars || [];
        replaceOptions(car, carChoices.map(choice => ({
            id: choice.id,
            label: choice.label + (!category && usedCars.includes(choice.id) ? ' (already used)' : ''),
            disabled: category ? !frozen || Boolean(selected?.consumed) : usedCars.includes(choice.id)
        })), car.value, 'Select car', sameSeason && reason !== 'entry');
        if (!category && car.selectedOptions[0]?.disabled) car.value = '';
        const usedTracks = data.usedTrackIds || [];
        replaceOptions(track, (data.tracks || []).map(choice => ({
            id: choice.id,
            label: choice.label + (usedTracks.includes(choice.id) ? ' (already used)' : ''),
            disabled: usedTracks.includes(choice.id)
        })), track.value, 'Select track', sameSeason);
        if (track.selectedOptions[0]?.disabled) track.value = '';
        hint.textContent = !category ? "Choose from this season's catalog. Previously used choices are unavailable."
            : !frozen ? "Freeze this season's category pool before choosing a car."
            : selected?.reason || 'Choose one entry, then one concrete car. Saving consumes the whole entry for this home team.';
        renderedSeason = season;
        syncDefaults();
        status.textContent = 'Availability updated for the home team.';
    }

    home.addEventListener('change', () => refresh('home'));
    matchday.addEventListener('change', () => refresh('matchday'));
    entry.addEventListener('change', () => { syncDefaults(); refresh('entry'); });
    car.addEventListener('change', syncDefaults);
    track.addEventListener('change', syncDefaults);
    syncDefaults();
})();
