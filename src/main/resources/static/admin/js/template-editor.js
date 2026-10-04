(function () {
    const root = document.querySelector('[data-template-type]');
    if (!root) return;
    const textarea = root.querySelector('.template-textarea');
    const iframe = root.querySelector('.preview-frame');
    const button = root.querySelector('.refresh-preview-btn');
    const status = root.querySelector('#template-preview-status');
    const area = root.querySelector('.editor-preview-area');
    const frameContainer = iframe.parentElement;
    const portrait = root.dataset.previewMode === 'portrait';
    const graphicWidth = portrait ? 1080 : 1920;
    const graphicHeight = portrait ? 1920 : 1080;
    let pending = false;
    let previewSource = null;

    function fitPreview() {
        const style = getComputedStyle(area);
        const available = area.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
        const width = Math.max(0, Math.min(available, portrait ? 270 : 640));
        frameContainer.style.width = width + 'px';
        frameContainer.style.height = width * graphicHeight / graphicWidth + 'px';
        iframe.style.transform = 'scale(' + width / graphicWidth + ')';
    }

    async function refresh() {
        if (pending) return;
        pending = true;
        const source = textarea.value;
        button.disabled = true;
        button.textContent = 'Loading...';
        area.setAttribute('aria-busy', 'true');
        status.classList.remove('field-error');
        status.textContent = 'Loading preview...';
        try {
            const response = await csrfFetch('/admin/tools/template-editors/' + root.dataset.templateType + '/preview', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: 'template=' + encodeURIComponent(source)
            });
            const html = await response.text();
            if (!response.ok) throw new Error(html || 'Request failed (' + response.status + ').');
            iframe.srcdoc = html;
            previewSource = source;
            status.textContent = textarea.value === source ? 'Preview updated from the current text.' : 'Preview shows an earlier edit. Refresh to preview the current text.';
        } catch (error) {
            status.classList.add('field-error');
            status.textContent = 'Preview failed: ' + error.message;
        } finally {
            pending = false;
            button.disabled = false;
            button.textContent = 'Refresh';
            area.removeAttribute('aria-busy');
        }
    }

    textarea.addEventListener('input', function () {
        if (previewSource !== null && textarea.value !== previewSource && !pending) {
            status.classList.remove('field-error');
            status.textContent = 'The text has changed. Refresh to preview the current version.';
        }
    });
    button.addEventListener('click', refresh);
    new ResizeObserver(fitPreview).observe(area);
    fitPreview();
    refresh();
})();
