(function () {
    const seasonForm = document.getElementById('seasonForm');
    if (seasonForm) {
        seasonForm.hidden = false;
        seasonForm.addEventListener('submit', function () {
            const selection = document.getElementById('seasonSelect').value.split('|');
            document.getElementById('yearInput').value = selection[0];
            document.getElementById('numberInput').value = selection[1];
        });
    }

    const list = document.getElementById('teamList');
    if (!list) return;
    const initialOrder = Array.from(list.children);
    const status = document.getElementById('ranking-status');
    const reset = document.querySelector('[data-reset-ranking]');
    let draggedItem = null;

    document.querySelectorAll('[data-ranking-instructions]').forEach(function (element) { element.hidden = false; });
    reset.hidden = false;
    initialOrder.forEach(function (item) { item.draggable = true; });

    function updateRanks() {
        const items = Array.from(list.children);
        items.forEach(function (item, index) {
            item.querySelector('.ranking-rank').textContent = index + 1;
            item.querySelector('[data-rank-up]').disabled = index === 0;
            item.querySelector('[data-rank-down]').disabled = index === items.length - 1;
        });
    }

    function announce(item) {
        const position = Array.from(list.children).indexOf(item) + 1;
        status.textContent = item.dataset.teamName + ' moved to position ' + position + ' of ' + list.children.length + '.';
    }

    list.addEventListener('click', function (event) {
        const button = event.target.closest('[data-rank-up], [data-rank-down]');
        if (!button || button.disabled) return;
        const item = button.closest('.ranking-item');
        const movingUp = button.hasAttribute('data-rank-up');
        const neighbour = movingUp ? item.previousElementSibling : item.nextElementSibling;
        if (!neighbour) return;
        if (movingUp) list.insertBefore(item, neighbour);
        else list.insertBefore(neighbour, item);
        updateRanks();
        announce(item);
        const focusTarget = button.disabled ? item.querySelector(movingUp ? '[data-rank-down]' : '[data-rank-up]') : button;
        focusTarget.focus();
    });

    reset.addEventListener('click', function () {
        initialOrder.forEach(function (item) { list.appendChild(item); });
        updateRanks();
        status.textContent = 'Initial rating order restored.';
        reset.focus();
    });

    list.addEventListener('dragstart', function (event) {
        const item = event.target.closest('.ranking-item');
        if (!item) return;
        draggedItem = item;
        item.classList.add('dragging');
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', item.dataset.teamId);
    });

    list.addEventListener('dragover', function (event) {
        if (!draggedItem) return;
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
        const target = event.target.closest('.ranking-item');
        if (!target || target === draggedItem) return;
        list.querySelectorAll('.drag-over').forEach(function (item) { item.classList.remove('drag-over'); });
        target.classList.add('drag-over');
        const bounds = target.getBoundingClientRect();
        list.insertBefore(draggedItem, event.clientY < bounds.top + bounds.height / 2 ? target : target.nextSibling);
        updateRanks();
    });

    list.addEventListener('drop', function (event) { if (draggedItem) event.preventDefault(); });
    list.addEventListener('dragend', function () {
        if (!draggedItem) return;
        draggedItem.classList.remove('dragging');
        list.querySelectorAll('.drag-over').forEach(function (item) { item.classList.remove('drag-over'); });
        updateRanks();
        announce(draggedItem);
        draggedItem = null;
    });
    updateRanks();
})();
