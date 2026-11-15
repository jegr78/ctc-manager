(function () {
    function restoreFocus() {
        var target = window.location.hash.slice(1);
        var upload = document.querySelector('.catalog-gallery .gallery-upload input[type=file]');
        if (/^(car|track)-images$/.test(target)) {
            if (upload) upload.focus();
            return;
        }
        if (!/^image-[0-9a-f-]+-(up|down)$/.test(target)) return;
        var control = document.getElementById(target);
        if (control && control.disabled) {
            var item = control.closest('.gallery-item');
            control = item ? item.querySelector('.gallery-actions button:not([disabled])') : null;
        }
        if (!control) control = upload;
        if (control) control.focus();
    }
    // Fragment navigation finishes after load and would otherwise reset the focus.
    if (document.readyState === 'complete') setTimeout(restoreFocus, 0);
    else window.addEventListener('load', function () { setTimeout(restoreFocus, 0); });
})();
