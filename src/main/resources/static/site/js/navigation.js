(function () {
    const menu = document.querySelector('.site-menu');
    const summary = menu && menu.querySelector('summary');
    if (!menu || !summary) return;

    function close(restoreFocus) {
        if (!menu.open) return;
        menu.open = false;
        if (restoreFocus) summary.focus();
    }

    function fitMenu() {
        const available = window.innerHeight - summary.getBoundingClientRect().bottom - 16;
        menu.style.setProperty('--menu-available-height', Math.max(44, available) + 'px');
    }
    menu.addEventListener('toggle', function () { if (menu.open) fitMenu(); });
    window.addEventListener('resize', fitMenu);
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && menu.open) {
            event.preventDefault();
            close(menu.contains(document.activeElement));
        }
    });
    document.addEventListener('pointerdown', function (event) {
        if (!menu.contains(event.target)) close(false);
    });
    menu.addEventListener('focusout', function () {
        requestAnimationFrame(function () {
            if (!menu.contains(document.activeElement)) close(false);
        });
    });
    menu.querySelectorAll('a[href]').forEach(function (link) {
        link.addEventListener('click', function () { close(false); });
    });
    window.addEventListener('pageshow', function () { close(false); });
})();
