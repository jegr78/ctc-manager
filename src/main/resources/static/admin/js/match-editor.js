(() => {
    const bye = document.getElementById('bye');
    const awayGroup = document.getElementById('awayTeamGroup');
    const awayTeam = document.getElementById('awayTeamId');
    if (!bye || !awayGroup || !awayTeam) return;

    function updateMatchType() {
        awayGroup.hidden = bye.checked;
        awayTeam.disabled = bye.checked;
    }
    bye.addEventListener('change', updateMatchType);
    updateMatchType();
})();
