(function () {
    var button = document.querySelector('[data-error-back]');
    if (!button || !document.referrer || history.length < 2) return;
    var previous = new URL(document.referrer);
    if (previous.origin !== location.origin || previous.href === location.href
        || (previous.pathname !== '/admin' && !previous.pathname.startsWith('/admin/'))) return;
    button.hidden = false;
    button.addEventListener('click', function () { history.back(); });
})();
