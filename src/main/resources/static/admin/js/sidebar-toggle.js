(function () {
    const layout = document.querySelector('.app-layout');
    const toggle = document.querySelector('.sidebar-toggle');
    const sidebar = document.querySelector('.sidebar');
    const closeButton = document.querySelector('.sidebar-close');
    const overlay = document.querySelector('.sidebar-overlay');
    const main = document.querySelector('.main-content');
    const skipLink = document.querySelector('.skip-link');
    if (!layout || !toggle || !sidebar || !closeButton || !overlay || !main) return;

    const mobile = window.matchMedia('(max-width: 768px)');
    let isOpen = false;
    layout.classList.add('nav-enhanced');

    function render() {
        const expanded = mobile.matches && isOpen;
        sidebar.classList.toggle('open', expanded);
        overlay.classList.toggle('visible', expanded);
        document.body.classList.toggle('admin-nav-open', expanded);
        toggle.setAttribute('aria-expanded', String(expanded));
        toggle.hidden = !mobile.matches || expanded;
        closeButton.hidden = !mobile.matches;
        sidebar.inert = mobile.matches && !expanded;
        main.inert = expanded;
        if (skipLink) skipLink.inert = expanded;
        if (sidebar.inert) sidebar.setAttribute('aria-hidden', 'true');
        else sidebar.removeAttribute('aria-hidden');
        if (expanded) {
            sidebar.setAttribute('role', 'dialog');
            sidebar.setAttribute('aria-modal', 'true');
        } else {
            sidebar.removeAttribute('role');
            sidebar.removeAttribute('aria-modal');
        }
    }

    function close(restoreFocus) {
        const wasOpen = isOpen;
        isOpen = false;
        render();
        if (wasOpen && restoreFocus && mobile.matches) toggle.focus();
    }

    toggle.addEventListener('click', function () {
        isOpen = true;
        render();
        closeButton.focus();
    });
    closeButton.addEventListener('click', function () { close(true); });
    overlay.addEventListener('click', function () { close(true); });
    sidebar.querySelectorAll('a[href]').forEach(function (link) {
        link.addEventListener('click', function () { close(false); });
    });

    document.addEventListener('keydown', function (event) {
        if (!mobile.matches || !isOpen) return;
        if (event.key === 'Escape') {
            event.preventDefault();
            close(true);
        } else if (event.key === 'Tab') {
            const controls = Array.from(sidebar.querySelectorAll('button:not([disabled]), a[href]'))
                .filter(function (control) { return !control.hidden; });
            const first = controls[0];
            const last = controls[controls.length - 1];
            if (event.shiftKey && document.activeElement === first) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
            }
        }
    });

    mobile.addEventListener('change', function () {
        const active = document.activeElement;
        const navigationFocused = sidebar.contains(active) || active === toggle;
        close(false);
        if (mobile.matches && navigationFocused) toggle.focus();
        else if (!mobile.matches && (active === closeButton || active === toggle)) {
            const link = sidebar.querySelector('a[aria-current="page"]') || sidebar.querySelector('a[href]');
            if (link) link.focus();
        }
    });
    window.addEventListener('pageshow', function () { close(true); });
    render();
})();
