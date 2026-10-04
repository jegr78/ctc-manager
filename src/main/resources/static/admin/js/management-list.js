(function () {
    document.querySelectorAll('[data-admin-list]').forEach(function (root) {
    var search = root.querySelector('[data-list-search]');
    var status = root.querySelector('[data-list-status]');
    var reset = root.querySelector('[data-list-reset]');
    var count = root.querySelector('[data-list-count]');
    var empty = root.querySelector('[data-list-empty]');
    var table = root.querySelector('[data-list-table]');
    var entries = Array.from(root.querySelectorAll('[data-list-entry]'));
    var pageSize = Number(root.dataset.pageSize);
    var pagination = root.querySelector('[data-list-pagination]');
    var pageInfo = root.querySelector('[data-list-page-info]');
    var previous = root.querySelector('[data-list-prev]');
    var next = root.querySelector('[data-list-next]');
    var page = 0;
    var sortColumn = -1;
    var ascending = true;

    function searchableText(entry) {
        return entry.dataset.search || Array.from(entry.querySelectorAll('[data-search]'))
            .map(function (row) { return row.dataset.search; }).join(' ');
    }
    function render() {
        var query = search.value.trim().toLocaleLowerCase();
        var selectedStatus = status ? status.value : 'all';
        var filtered = entries.filter(function (entry) {
            return searchableText(entry).toLocaleLowerCase().includes(query)
                && (selectedStatus === 'all' || entry.dataset.status === selectedStatus);
        });
        var totalPages = pageSize ? Math.max(1, Math.ceil(filtered.length / pageSize)) : 1;
        page = Math.max(0, Math.min(page, totalPages - 1));
        var start = pageSize ? page * pageSize : 0;
        var end = pageSize ? Math.min(start + pageSize, filtered.length) : filtered.length;
        entries.forEach(function (entry) { entry.hidden = true; });
        filtered.slice(start, end).forEach(function (entry) { entry.hidden = false; });
        count.textContent = filtered.length + ' ' + (filtered.length === 1 ? root.dataset.unit : root.dataset.plural)
            + (pageSize && filtered.length ? '. Showing ' + (start + 1) + ' to ' + end + '.' : '');
        var filtering = Boolean(query || selectedStatus !== 'all');
        empty.hidden = filtered.length > 0 || !filtering;
        var initialEmpty = root.querySelector('[data-list-initial-empty]');
        if (initialEmpty) initialEmpty.hidden = filtering;
        if (table) table.hidden = filtered.length === 0;
        reset.disabled = !search.value && selectedStatus === 'all';
        if (pagination) {
            pagination.hidden = filtered.length <= pageSize;
            pageInfo.textContent = 'Page ' + (page + 1) + ' of ' + totalPages;
            previous.disabled = page === 0;
            next.disabled = page === totalPages - 1;
        }
    }
    function updateFilters() { page = 0; render(); }
    search.addEventListener('input', updateFilters);
    if (status) status.addEventListener('change', updateFilters);
    reset.addEventListener('click', function () {
        search.value = '';
        if (status) status.value = 'all';
        updateFilters();
        search.focus();
    });
    if (previous) previous.addEventListener('click', function () { page--; render(); });
    if (next) next.addEventListener('click', function () { page++; render(); });
    root.querySelectorAll('th.sortable').forEach(function (header) {
        var button = header.querySelector('button');
        button.disabled = false;
        button.addEventListener('click', function () {
            var column = header.cellIndex;
            ascending = sortColumn === column ? !ascending : true;
            sortColumn = column;
            entries.sort(function (a, b) {
                var aCell = a.children[column];
                var bCell = b.children[column];
                var aValue = aCell.dataset.sortValue || aCell.textContent.trim();
                var bValue = bCell.dataset.sortValue || bCell.textContent.trim();
                var comparison = header.dataset.type === 'num'
                    ? Number(aValue) - Number(bValue)
                    : aValue.localeCompare(bValue, undefined, {sensitivity: 'base', numeric: true});
                return ascending ? comparison : -comparison;
            });
            entries.forEach(function (entry) { entry.parentElement.appendChild(entry); });
            root.querySelectorAll('th.sortable').forEach(function (other) {
                other.dataset.sort = other === header ? (ascending ? 'asc' : 'desc') : '';
                other.setAttribute('aria-sort', other === header ? (ascending ? 'ascending' : 'descending') : 'none');
            });
            page = 0;
            render();
        });
    });
    root.querySelector('[data-list-tools]').hidden = false;
    render();
    });
})();
