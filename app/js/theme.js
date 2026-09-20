(function () {

    var KEY = 'daiji-theme';          // 'dark' | 'light'
    var root = document.documentElement;


    /* ---------- lê a preferência salva ---------- */

    function saved() {
        try {
            return localStorage.getItem(KEY);
        } catch (e) {
            return null;
        }
    }


    /* ---------- aplica um tema ---------- */

    function apply(theme) {

        if (theme === 'light') {
            root.setAttribute('data-theme', 'light');
        } else {
            theme = 'dark';
            root.removeAttribute('data-theme');
        }

        try {
            localStorage.setItem(KEY, theme);
        } catch (e) { /* modo privado etc. */ }

        sync(theme);
    }


    /* ---------- atualiza o estado visual dos controles ---------- */

    function sync(theme) {

        var options = document.querySelectorAll('[data-theme-set]');

        options.forEach(function (el) {

            var on = el.getAttribute('data-theme-set') === theme;

            el.classList.toggle('is-active', on);
            el.setAttribute('aria-pressed', on ? 'true' : 'false');

            if (el.matches('input[type="radio"]')) {
                el.checked = on;
            }
        });
    }


    /* ---------- tema atual ---------- */

    function current() {
        return root.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
    }


    /* aplica imediatamente (antes do <body> pintar) */
    apply(saved() || 'dark');


    /* ---------- conecta os controles quando o DOM estiver pronto ---------- */

    function wire() {

        var options = document.querySelectorAll('[data-theme-set]');

        options.forEach(function (el) {

            el.addEventListener('click', function () {
                apply(el.getAttribute('data-theme-set'));
            });

            // suporte a teclado para elementos não-nativos
            if (!el.matches('input, button, a')) {
                el.setAttribute('role', el.getAttribute('role') || 'button');
                el.setAttribute('tabindex', el.getAttribute('tabindex') || '0');
                el.addEventListener('keydown', function (ev) {
                    if (ev.key === 'Enter' || ev.key === ' ') {
                        ev.preventDefault();
                        apply(el.getAttribute('data-theme-set'));
                    }
                });
            }
        });

        // botões de alternância simples marcados com [data-theme-toggle]
        document.querySelectorAll('[data-theme-toggle]').forEach(function (el) {
            el.addEventListener('click', function () {
                apply(current() === 'light' ? 'dark' : 'light');
            });
        });

        sync(current());
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', wire);
    } else {
        wire();
    }


    /* expõe uma API mínima, caso alguma página precise */
    window.DaijiTheme = {
        set: apply,
        toggle: function () { apply(current() === 'light' ? 'dark' : 'light'); },
        current: current
    };

})();
