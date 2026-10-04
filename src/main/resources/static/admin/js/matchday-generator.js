(() => {
    const form = document.getElementById('matchday-generator-form');
    if (!form) return;
    const rounds = document.getElementById('numberOfRounds');
    const homeAway = document.getElementById('homeAndAway');
    const group = document.getElementById('groupId');
    const roster = document.getElementById('generator-roster');
    const summary = document.getElementById('summary');

    function update() {
        const source = group ? group.selectedOptions[0] : form;
        const count = Number(source?.dataset.teamCount ?? 0);
        const recommended = Number(source?.dataset.optimalRounds ?? 0);
        roster.textContent = `${count} teams · Recommended rounds for a full round-robin: ${recommended}`;
        const value = Number(rounds.value);
        if (count < 2) {
            summary.textContent = 'At least two assigned teams are needed to generate matchdays.';
        } else {
            summary.textContent = rounds.value && Number.isInteger(value) && value >= 1
                ? `Total matchdays: ${Math.min(value, recommended) * (homeAway.checked ? 2 : 1)}`
                : 'Enter at least one whole round to calculate the matchdays.';
        }
    }
    rounds.addEventListener('input', update);
    homeAway.addEventListener('change', update);
    group?.addEventListener('change', update);
    update();
})();
