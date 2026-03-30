(function () {
    const toggle = document.querySelector('.sidebar-toggle');
    const sidebar = document.querySelector('.sidebar');
    const overlay = document.querySelector('.sidebar-overlay');
    if (!toggle || !sidebar || !overlay) return;

    function open() {
        sidebar.classList.add('open');
        overlay.classList.add('visible');
        toggle.setAttribute('aria-expanded', 'true');
    }

    function close() {
        sidebar.classList.remove('open');
        overlay.classList.remove('visible');
        toggle.setAttribute('aria-expanded', 'false');
    }

    toggle.addEventListener('click', function () {
        sidebar.classList.contains('open') ? close() : open();
    });

    overlay.addEventListener('click', close);

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && sidebar.classList.contains('open')) close();
    });
})();
