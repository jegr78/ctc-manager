(function () {
    var container = document.getElementById('aliases-container');
    var add = document.querySelector('[data-add-alias]');
    if (!container || !add) return;
    function reindex() {
        Array.from(container.children).forEach(function (row, index) {
            var input = row.querySelector('input');
            input.id = 'alias-' + index;
            input.name = 'aliases[' + index + ']';
            var label = row.querySelector('label');
            label.htmlFor = input.id;
            label.textContent = 'PSN alias ' + (index + 1);
            var remove = row.querySelector('[data-remove-alias]');
            remove.hidden = false;
            remove.setAttribute('aria-label', 'Remove PSN alias ' + (index + 1));
        });
    }
    add.addEventListener('click', function () {
        var row = document.createElement('div');
        row.className = 'alias-row';
        var label = document.createElement('label');
        var input = document.createElement('input');
        input.type = 'text';
        input.className = 'alias-input';
        input.placeholder = 'Previous PSN ID';
        input.setAttribute('aria-describedby', 'aliases-hint');
        var remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'btn btn-secondary';
        remove.dataset.removeAlias = '';
        remove.textContent = 'Remove';
        row.append(label, input, remove);
        container.appendChild(row);
        reindex();
        input.focus();
    });
    container.addEventListener('click', function (event) {
        var remove = event.target.closest('[data-remove-alias]');
        if (!remove || !container.contains(remove)) return;
        var row = remove.parentElement;
        var index = Array.from(container.children).indexOf(row);
        row.remove();
        reindex();
        var inputs = container.querySelectorAll('input');
        var target = inputs[Math.min(index, inputs.length - 1)] || add;
        target.focus();
    });
    container.closest('form').addEventListener('submit', reindex);
    add.hidden = false;
    reindex();
})();
