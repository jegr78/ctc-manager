(function () {
    var tables = document.querySelectorAll('.race-full-table');
    var desktop = window.matchMedia('(min-width: 769px)');
    function updateLayout() {
        tables.forEach(function (table) { table.open = desktop.matches; });
    }
    desktop.addEventListener('change', updateLayout);
    updateLayout();
})();
