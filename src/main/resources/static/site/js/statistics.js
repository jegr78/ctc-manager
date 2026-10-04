(function () {
    var desktop = window.matchMedia('(min-width: 769px)');
    var tables = document.querySelectorAll('.responsive-table');
    function updateLayout() {
        tables.forEach(function (table) { table.open = desktop.matches; });
    }
    desktop.addEventListener('change', updateLayout);
    updateLayout();
})();
