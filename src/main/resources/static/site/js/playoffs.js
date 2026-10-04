(function () {
    var rounds = document.querySelectorAll('.playoff-round');
    var desktop = window.matchMedia('(min-width: 769px)');
    function openTarget(hash) {
        var target = document.getElementById(hash.slice(1));
        if (target && target.classList.contains('playoff-round')) target.open = true;
    }
    function updateLayout() {
        rounds.forEach(function (round, index) { round.open = desktop.matches || index === 0; });
        openTarget(window.location.hash);
    }
    document.querySelectorAll('.playoff-round-nav a').forEach(function (link) {
        link.addEventListener('click', function () { openTarget(link.getAttribute('href')); });
    });
    window.addEventListener('hashchange', function () { openTarget(window.location.hash); });
    desktop.addEventListener('change', updateLayout);
    updateLayout();
})();
