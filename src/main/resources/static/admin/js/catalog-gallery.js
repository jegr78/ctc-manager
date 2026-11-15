(function () {
    var target = window.location.hash.slice(1);
    if (/^(car|track)-images$/.test(target)) {
        var upload = document.querySelector('.catalog-gallery .gallery-upload input[type=file]');
        if (upload) upload.focus();
        return;
    }
    if (!/^image-[0-9a-f-]+-(up|down)$/.test(target)) return;
    var control = document.getElementById(target);
    if (control && control.disabled) {
        var item = control.closest('.gallery-item');
        control = item ? item.querySelector('.gallery-actions button:not([disabled])') : null;
    }
    if (!control) control = document.querySelector('.catalog-gallery .gallery-upload input[type=file]');
    if (control) control.focus();
})();
