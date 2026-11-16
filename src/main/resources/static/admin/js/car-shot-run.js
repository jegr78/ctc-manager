(function () {
    var status = document.querySelector('[data-car-shot-status]');
    if (!status || status.dataset.running !== 'true') return;
    function poll() {
        fetch('/admin/cars/gt7-images/status', {headers: {Accept: 'application/json'}})
            .then(function (response) { return response.ok ? response.json() : null; })
            .then(function (run) {
                if (!run) return;
                status.textContent = run.message;
                if (run.running) setTimeout(poll, 5000);
                else window.location.reload();
            })
            .catch(function () { setTimeout(poll, 15000); });
    }
    setTimeout(poll, 5000);
})();
