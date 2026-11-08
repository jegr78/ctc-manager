(function () {
    const form = document.querySelector('[data-swiss-editor]');
    if (!form) return;
    const previousPairs = new Set(Array.from(form.querySelectorAll('[data-previous-pair]')).map(item => item.dataset.previousPair));
    const previousByes = new Set(Array.from(form.querySelectorAll('[data-previous-bye]')).map(item => item.dataset.previousBye));
    function refresh() {
        const warnings = [];
        const used = new Set();
        form.querySelectorAll('[data-swiss-row]').forEach((row, index) => {
            const bye = row.querySelector('[data-swiss-bye]');
            const away = row.querySelector('[data-swiss-away]');
            const home = row.querySelector('[data-swiss-home]');
            const remove = row.querySelector('[data-swiss-remove]');
            away.disabled = home.disabled || bye.checked;
            if (remove.checked) return;
            const homePlace = home.selectedOptions[0]?.dataset.placeId;
            const awayPlace = away.selectedOptions[0]?.dataset.placeId;
            if (homePlace && bye.checked && previousByes.has(homePlace)) warnings.push('Pairing ' + (index + 1) + ': this team has received a bye before. Keep it if this is the actual historical pairing.');
            else if (homePlace && awayPlace && !bye.checked && previousPairs.has([homePlace, awayPlace].sort().join(':'))) warnings.push('Pairing ' + (index + 1) + ': these teams have played before. Historical rematches are allowed.');
            [home, bye.checked ? null : away].filter(Boolean).forEach(select => {
                const option = select.selectedOptions[0];
                if (option && option.dataset.placeId) used.add(option.dataset.placeId);
            });
        });
        const missing = Array.from(form.querySelectorAll('[data-swiss-places] [data-place-id]'))
            .filter(item => !used.has(item.dataset.placeId)).map(item => item.textContent);
        const warningBox = form.querySelector('[data-swiss-warnings]');
        warningBox.replaceChildren(...warnings.map(message => { const item = document.createElement('p'); item.textContent = message; return item; }));
        warningBox.hidden = warnings.length === 0;
        form.querySelector('[data-swiss-missing]').textContent = missing.length ? missing.join(', ') : 'All places are assigned.';
    }
    form.addEventListener('change', refresh);
    refresh();
})();
