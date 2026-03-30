document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.color-pair').forEach(function (pair) {
        var picker = pair.querySelector('input[type="color"]');
        var text = pair.querySelector('input[type="text"]');
        if (!picker || !text) return;

        picker.addEventListener('input', function () {
            text.value = picker.value;
        });
        text.addEventListener('input', function () {
            if (/^#[0-9a-fA-F]{6}$/.test(text.value)) {
                picker.value = text.value;
            }
        });
    });
});
