(function () {
    document.querySelectorAll('[data-trailer-status][data-running="true"]').forEach(function (status) {
        function poll() {
            fetch(status.dataset.statusUrl, {headers: {Accept: 'application/json'}})
                .then(function (response) {
                    if (!response.ok) throw new Error('status ' + response.status);
                    return response.json();
                })
                .then(function (job) {
                    status.textContent = job.message;
                    if (job.running) setTimeout(poll, 3000);
                    else window.location.reload();
                })
                .catch(function () { setTimeout(poll, 10000); });
        }
        setTimeout(poll, 3000);
    });
})();
