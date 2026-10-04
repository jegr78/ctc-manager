(function () {
    var form = document.getElementById('scoringForm');
    if (!form) return;
    form.querySelectorAll('[data-points-editor]').forEach(function (editor) {
        var type = editor.dataset.pointsEditor;
        var source = document.getElementById(type + 'Hidden');
        var rows = document.getElementById(type + 'Rows');
        var add = editor.querySelector('[data-points-add]');
        var remove = editor.querySelector('[data-points-remove]');
        var count = editor.querySelector('[data-points-count]');
        function collect() {
            source.value = Array.from(rows.querySelectorAll('input')).map(function (input) { return input.value; }).join(',');
            count.textContent = rows.children.length + (rows.children.length === 1 ? ' position' : ' positions');
            remove.disabled = rows.children.length === 0;
        }
        function append(value) {
            var position = rows.children.length + 1;
            var row = document.createElement('div');
            row.className = 'scoring-position-row';
            var label = document.createElement('label');
            var input = document.createElement('input');
            input.type = 'number';
            input.min = '0';
            input.step = '1';
            input.required = true;
            input.id = type + '-position-' + position;
            input.value = value || '0';
            input.setAttribute('aria-describedby', type + '-hint');
            label.htmlFor = input.id;
            label.textContent = 'Position ' + position;
            input.addEventListener('input', collect);
            row.append(label, input);
            rows.appendChild(row);
            return input;
        }
        var initial = source.value.trim();
        if (initial) initial.split(',').forEach(function (value) { append(value.trim()); });
        add.addEventListener('click', function () {
            var last = rows.lastElementChild;
            var value = last ? last.querySelector('input').value : '0';
            var input = append(value);
            collect();
            input.focus();
        });
        remove.addEventListener('click', function () {
            if (rows.lastElementChild) rows.lastElementChild.remove();
            collect();
            var last = rows.lastElementChild;
            (last ? last.querySelector('input') : add).focus();
        });
        source.type = 'hidden';
        editor.querySelector('[data-points-source]').hidden = true;
        editor.querySelector('[data-points-controls]').hidden = false;
        form.addEventListener('submit', collect);
        collect();
    });
})();
