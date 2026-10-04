(() => {
    const editor = document.querySelector('.race-setup-editor');
    if (!editor) return;
    const home = editor.querySelector('#homeTeamId');
    const matchday = editor.querySelector('#matchdayId');
    const status = editor.querySelector('[data-used-selection-status]');
    const catalogs = [editor.querySelector('#carId'), editor.querySelector('#trackId')];
    catalogs.forEach(select => Array.from(select.options).forEach(option => {
        option.dataset.label = option.textContent.replace(/ \(already used\)$/, '');
    }));
    let request;
    home.addEventListener('change', async () => {
        if (request) request.abort();
        const season = matchday.selectedOptions[0]?.dataset.seasonId;
        if (!home.value || !season) {
            catalogs.forEach(select => Array.from(select.options).forEach(option => {
                option.disabled = false;
                option.textContent = option.dataset.label;
            }));
            status.textContent = '';
            return;
        }
        request = new AbortController();
        const params = new URLSearchParams({seasonId: season, homeTeamId: home.value});
        const raceId = editor.querySelector('input[name="id"]').value;
        if (raceId) params.set('excludeRaceId', raceId);
        status.textContent = 'Checking previously used cars and tracks…';
        try {
            const response = await fetch('/admin/races/used-selections?' + params, {signal: request.signal});
            if (!response.ok) throw new Error('Selection check failed');
            const data = await response.json();
            catalogs.forEach(select => {
                const used = data[select.id === 'carId' ? 'usedCarIds' : 'usedTrackIds'] || [];
                Array.from(select.options).forEach(option => {
                    option.disabled = used.includes(option.value);
                    option.textContent = option.dataset.label + (option.disabled ? ' (already used)' : '');
                });
                if (select.selectedOptions[0]?.disabled) select.value = '';
            });
            status.textContent = 'Availability updated for the home team.';
        } catch (error) {
            if (error.name !== 'AbortError') status.textContent = 'Could not check availability. Try selecting the home team again.';
        }
    });
})();
