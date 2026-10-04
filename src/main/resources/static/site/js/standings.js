(function () {
    var table = document.getElementById('full-standings');
    var link = document.querySelector('.full-standings-link');
    if (!table || !link) return;

    var desktop = window.matchMedia('(min-width: 769px)');
    function updateLayout() { table.open = desktop.matches; }
    desktop.addEventListener('change', updateLayout);
    updateLayout();
    link.addEventListener('click', function () { table.open = true; });
})();
