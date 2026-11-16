(function () {
    var status = document.querySelector('[data-car-shot-status]');
    if (!status || status.dataset.running !== 'true') return;
    function poll() {
        fetch('/admin/cars/gt7-images/status', {headers: {Accept: 'application/json'}})
            .then(function (response) {
                if (!response.ok) throw new Error('status ' + response.status);
                return response.json();
            })
            .then(function (run) {
                status.textContent = run.message;
                if (run.running) setTimeout(poll, 5000);
                else window.location.reload();
            })
            .catch(function () { setTimeout(poll, 15000); });
    }
    setTimeout(poll, 5000);
})();
