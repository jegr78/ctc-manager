(function () {
    document.querySelectorAll('[data-role-picker]').forEach(function (picker) {
        const tools = picker.querySelector('.role-search-tools');
        const input = picker.querySelector('input[type="search"]');
        const select = picker.querySelector('select');
        const reset = picker.querySelector('[data-role-search-reset]');
        const count = picker.querySelector('[role="status"]');
        if (!tools || !input || !select || !reset || !count) return;
        const blank = select.options[0];
        const options = Array.from(select.options).slice(1);
        const collator = new Intl.Collator(undefined, { sensitivity: 'base', numeric: true });
        options.sort(function (left, right) { return collator.compare(left.text, right.text); });
        const cached = options.filter(function (option) { return !option.hasAttribute('data-saved-role'); });

        function filter() {
            const value = select.value;
            const query = input.value.trim().toLocaleLowerCase();
            const matches = cached.filter(function (option) {
                return option.text.toLocaleLowerCase().includes(query) || option.value.includes(query);
            });
            const selected = options.find(function (option) { return option.value === value; });
            const visible = matches.slice();
            if (selected && !visible.includes(selected)) visible.unshift(selected);
            select.replaceChildren(blank, ...visible);
            select.value = value;
            reset.disabled = input.value.length === 0;
            count.textContent = matches.length + (matches.length === 1 ? ' matching role' : ' matching roles')
                + (query && selected && !matches.includes(selected) ? '. Selected role stays available.' : '.');
        }
        input.addEventListener('input', filter);
        select.addEventListener('blur', filter);
        reset.addEventListener('click', function () {
            input.value = '';
            filter();
            input.focus();
        });
        filter();
        tools.hidden = false;
    });
})();
