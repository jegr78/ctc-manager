(function () {
    var table = document.getElementById('archiveTable');
    var search = document.getElementById('archive-search');
    var order = document.getElementById('archive-order');
    var cards = document.querySelector('.mobile-archive');
    if (!table || !search || !order || !cards) return;
    var tbody = table.querySelector('tbody');
    var header = table.querySelector('th.sortable');
    var rows = Array.from(tbody.children);
    var seasons = Array.from(cards.children);
    function update() {
        var query = search.value.trim().toLocaleLowerCase();
        var direction = order.value === 'asc' ? 1 : -1;
        function compare(a, b) {
            return direction * (Number(a.dataset.year) - Number(b.dataset.year)
                || Number(a.dataset.number) - Number(b.dataset.number));
        }
        [rows, seasons].forEach(function (items) {
            items.sort(compare).forEach(function (item) {
                item.hidden = !item.dataset.search.toLocaleLowerCase().includes(query);
                item.parentElement.appendChild(item);
            });
        });
        header.dataset.sort = order.value;
        header.setAttribute('aria-sort', direction === 1 ? 'ascending' : 'descending');
        var count = rows.filter(function (row) { return !row.hidden; }).length;
        document.getElementById('archive-count').textContent = count + (count === 1 ? ' season' : ' seasons');
        document.getElementById('archive-no-matches').hidden = count > 0 || !query;
    }
    search.addEventListener('input', update);
    order.addEventListener('change', update);
    document.getElementById('archive-sort').addEventListener('click', function () {
        order.value = order.value === 'asc' ? 'desc' : 'asc';
        update();
    });
    document.querySelector('.archive-tools').hidden = false;
    update();
})();
