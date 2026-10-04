const teams = [
    {name: 'Alpha', fullName: 'Alpha Racing', color: 'yellow', group: 'A', played: 5, wins: 4, draws: 1, losses: 0, ratio: '1.24', points: 13},
    {name: 'Bravo', fullName: 'Bravo Motorsport', color: 'red', group: 'A', played: 5, wins: 3, draws: 1, losses: 1, ratio: '1.12', points: 10},
    {name: 'Charlie', fullName: 'Charlie Racing', color: 'blue', group: 'B', played: 5, wins: 3, draws: 0, losses: 2, ratio: '1.08', points: 9},
    {name: 'Delta', fullName: 'Delta Motorsport', color: 'purple', group: 'B', played: 5, wins: 2, draws: 1, losses: 2, ratio: '0.96', points: 7},
    {name: 'Echo', fullName: 'Echo Racing', color: 'green', group: 'A', played: 5, wins: 1, draws: 1, losses: 3, ratio: '0.89', points: 4},
    {name: 'Foxtrot', fullName: 'Foxtrot Motorsport', color: 'white', group: 'B', played: 5, wins: 0, draws: 0, losses: 5, ratio: '0.76', points: 0}
];

const groupFilter = document.querySelector('#group-filter');
if (groupFilter) {
    const fullTable = document.querySelector('#full-table');
    const desktop = window.matchMedia('(min-width: 760px)');
    const syncTable = () => { fullTable.open = desktop.matches; };
    desktop.addEventListener('change', syncTable);
    syncTable();
    const renderStandings = () => {
        const selection = groupFilter.value;
        const visible = teams.filter(team => selection === 'all' || team.group === selection);
        const name = team => `<span class="team-name"><i class="team-marker team-${team.color}" aria-hidden="true"></i>${team.name}</span>`;
        document.querySelector('#standings-count').textContent = `${visible.length} teams · ${selection === 'all' ? 'All groups' : `Group ${selection}`}`;
        document.querySelector('#mobile-rows').innerHTML = visible.map((team, index) => `<details class="standing">
            <summary><span class="number">${index + 1}</span><span>${name(team)}<span class="team-caption">${team.played} matches · ${team.wins} wins</span></span><span class="standing-points number">${team.points}<span class="skip"> points</span></span><span class="chevron" aria-hidden="true">⌄</span></summary>
            <div class="standing-details"><dl><div><dt>Group</dt><dd>${team.group}</dd></div><div><dt>Wins</dt><dd>${team.wins}</dd></div><div><dt>Draws</dt><dd>${team.draws}</dd></div><div><dt>Losses</dt><dd>${team.losses}</dd></div><div><dt>Points ratio</dt><dd>${team.ratio}</dd></div></dl><p class="meta">${team.fullName}</p>${team.name === 'Alpha' ? '<a href="#team-alpha">Team profile →</a>' : ''}</div>
        </details>`).join('');
        document.querySelector('#desktop-rows').innerHTML = visible.map((team, index) => `<tr><td>${index + 1}</td><th scope="row">${team.name === 'Alpha' ? `<a href="#team-alpha">${name(team)}</a>` : name(team)}</th><td>${team.group}</td><td class="align-right">${team.played}</td><td class="align-right">${team.wins}</td><td class="align-right">${team.draws}</td><td class="align-right">${team.losses}</td><td class="align-right">${team.ratio}</td><td class="align-right"><strong>${team.points}</strong></td></tr>`).join('');
    };
    groupFilter.addEventListener('change', renderStandings);
    renderStandings();
}

const panelButtons = document.querySelectorAll('[data-panel]');
const showPanel = id => {
    document.querySelectorAll('.workspace-panel').forEach(panel => { panel.hidden = panel.id !== id; });
    panelButtons.forEach(button => button.setAttribute('aria-pressed', String(button.dataset.panel === id)));
};
panelButtons.forEach(button => button.addEventListener('click', () => showPanel(button.dataset.panel)));
document.querySelectorAll('[data-open-panel]').forEach(link => link.addEventListener('click', () => {
    const row = link.closest('[data-match-status]');
    if (row && link.dataset.openPanel === 'results') {
        const names = [...row.querySelectorAll('.team-name')].map(team => team.textContent.trim());
        const complete = row.dataset.matchStatus === 'complete';
        document.querySelector('#results-title').textContent = complete ? 'Review results' : 'Enter results';
        document.querySelector('#result-context').textContent = `${names.join(' / ')} · ${row.dataset.matchStatus === 'partial' ? 'Leg 2 · Tsukuba Circuit' : 'Leg 1 · Daytona Tri-Oval'}`;
        document.querySelector('#result-score').textContent = row.querySelector('.score').textContent;
        document.querySelector('#home-points-label').textContent = `${names[0]} race points`;
        document.querySelector('#away-points-label').textContent = `${names[1]} race points`;
        document.querySelector('input[name="home"]').value = complete ? '64' : '';
        document.querySelector('input[name="away"]').value = complete ? '52' : '';
        document.querySelector('#save-status').textContent = 'No unsaved changes';
    }
    showPanel(link.dataset.openPanel);
}));
document.querySelector('a[href="#matches"][aria-current]')?.addEventListener('click', () => showPanel('matches'));

const search = document.querySelector('#match-search');
const status = document.querySelector('#match-status');
if (search && status) {
    const filterMatches = () => {
        let count = 0;
        document.querySelectorAll('[data-match-status]').forEach(row => {
            const matchesSearch = row.querySelector('.pairing').textContent.toLowerCase().includes(search.value.trim().toLowerCase());
            row.hidden = !matchesSearch || (status.value !== 'all' && row.dataset.matchStatus !== status.value);
            if (!row.hidden) count++;
        });
        document.querySelector('#match-empty').hidden = count !== 0;
        document.querySelector('#match-count').textContent = `${count} ${count === 1 ? 'match' : 'matches'}`;
    };
    search.addEventListener('input', filterMatches);
    status.addEventListener('change', filterMatches);
}

const resultForm = document.querySelector('#result-form');
if (resultForm) {
    const saveStatus = document.querySelector('#save-status');
    resultForm.addEventListener('input', () => { saveStatus.textContent = 'Unsaved changes'; });
    resultForm.addEventListener('submit', event => {
        event.preventDefault();
        saveStatus.textContent = 'Saved in this preview';
    });
}
