(() => {
    document.querySelectorAll('[data-tyre-inputs]').forEach(form => {
        const available = form.querySelector('[data-available-tyres]');
        const mandatory = form.querySelector('[data-mandatory-tyres]');
        const status = form.querySelector('[data-tyre-choice-status]');
        if (!available || !mandatory) return;

        function choices() {
            const selected = available instanceof HTMLSelectElement
                ? Array.from(available.selectedOptions)
                : Array.from(available.querySelectorAll('input[name="availableTyres"]:checked'));
            const values = selected.filter(input => input.dataset.tyreFamily).map(input => ({
                value: input.value,
                label: input instanceof HTMLOptionElement ? input.textContent : input.labels[0].querySelector('span').textContent,
                family: input.dataset.tyreFamily
            }));
            return new Map(values.map(value => [value.value, value]));
        }

        function update() {
            const selected = choices();
            const families = new Set(Array.from(selected.values()).map(value => value.family));
            const valid = families.size === 1;
            const values = valid ? Array.from(selected.values()) : [];
            if (mandatory instanceof HTMLSelectElement) {
                const retained = new Set(Array.from(mandatory.selectedOptions).map(option => option.value));
                const keep = values.some(value => retained.has(value.value));
                mandatory.replaceChildren(new Option('None', 'None', false, !keep));
                values.forEach(value => mandatory.add(new Option(value.label, value.value, false, retained.has(value.value))));
            } else {
                const retained = new Set(Array.from(mandatory.querySelectorAll('input[name="mandatoryTyres"]:checked')).map(input => input.value));
                const marker = document.createElement('input');
                marker.type = 'hidden'; marker.name = '_mandatoryTyres'; marker.value = 'on';
                mandatory.replaceChildren(marker);
                values.forEach(value => {
                    const label = document.createElement('label');
                    label.className = 'pick-check-option';
                    const input = document.createElement('input');
                    input.type = 'checkbox'; input.name = 'mandatoryTyres'; input.value = value.value;
                    input.id = mandatory.id + '-' + value.value; input.checked = retained.has(value.value);
                    label.htmlFor = input.id;
                    const text = document.createElement('span'); text.textContent = value.label;
                    label.append(input, text); mandatory.append(label);
                });
            }
            if (status) status.textContent = valid
                ? 'Mandatory choices updated from your available dry tyres.' + (families.has('RACING') ? ' Racing also includes Intermediate and Wet.' : '')
                : 'Choose available dry compounds from one family before choosing mandatory tyres.';
        }

        // Preserve rejected values on initial render; only an explicit availability change replaces them.
        available.addEventListener('change', update);
    });
})();
