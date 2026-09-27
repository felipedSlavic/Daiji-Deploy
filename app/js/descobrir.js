(function () {

    const MEDICOS = window.DAIJI_MEDICOS || []
    const ESPECIALIDADES = window.DAIJI_ESPECIALIDADES || {}

    const categorias = document.querySelectorAll('.category-item')
    const trendCards = document.querySelectorAll('.trend-card[data-specialty]')
    const buscaInput = document.getElementById('busca')
    const listaEl = document.getElementById('doctorsList')
    const contadorEl = document.getElementById('doctorsCount')
    const verMaisBtn = document.getElementById('doctorsMore')

    // quantos médicos aparecem em "Todos" antes do "Ver todos"
    const LIMITE_INICIAL = 8

    let categoriaAtiva = 'todos'
    let mostrarTodos = false


    /* normaliza texto para busca sem acento */
    function normalizar(texto) {
        return String(texto)
            .toLowerCase()
            .normalize('NFD')
            .replace(/[̀-ͯ]/g, '')
            .trim()
    }


    function cardMedico(m) {

        const esp = ESPECIALIDADES[m.especialidade] || {}

        return `
            <a
                href="agendar.html?medico=${m.id}"
                class="doctor-card"
                data-specialty="${m.especialidade}"
            >

                <div class="doctor-avatar doctor-avatar-icon doctor-initials">
                    ${window.DAIJI_iniciais(m.nome)}
                </div>

                <div class="doctor-info">

                    <h5>${m.nome}</h5>

                    <p class="doc-sub">
                        ${m.titulo} • ${m.registro}
                    </p>

                    <p class="doc-rating">
                        <i class="bi bi-star-fill"></i>
                        ${m.nota.toFixed(1)} (${m.avaliacoes} avaliações)
                        <span class="doc-dot">•</span>
                        <span class="doc-dist">
                            <i class="bi bi-geo-alt"></i>
                            ${m.distancia.toFixed(1).replace('.', ',')} km
                        </span>
                    </p>

                    <span class="doc-tag">
                        <i class="bi ${esp.icone || 'bi-plus'}"></i>
                        ${esp.nome || ''}
                    </span>

                </div>

                <button
                    type="button"
                    class="heart-btn"
                    aria-label="Favoritar ${m.nome}"
                >
                    <i class="bi bi-heart"></i>
                </button>

            </a>
        `
    }


    function aplicarFiltros() {

        const termo = normalizar(buscaInput ? buscaInput.value : '')


        /* cards "Em alta" */
        trendCards.forEach(card => {

            const passaCategoria =
                categoriaAtiva === 'todos' ||
                card.dataset.specialty === categoriaAtiva

            const passaBusca =
                !termo || normalizar(card.textContent).includes(termo)

            card.style.display = (passaCategoria && passaBusca) ? '' : 'none'

        })


        /* médicos */
        if (!listaEl) return

        const filtrados = MEDICOS
            .filter(m => {

                const esp = ESPECIALIDADES[m.especialidade] || {}

                const passaCategoria =
                    categoriaAtiva === 'todos' ||
                    m.especialidade === categoriaAtiva

                const texto = normalizar(
                    [m.nome, m.titulo, m.registro, esp.nome, esp.categoria].join(' ')
                )

                const passaBusca = !termo || texto.includes(termo)

                return passaCategoria && passaBusca

            })
            .sort((a, b) => a.distancia - b.distancia)


        // limita só na visão geral, sem busca
        const limitar =
            categoriaAtiva === 'todos' && !termo && !mostrarTodos

        const visiveis =
            limitar ? filtrados.slice(0, LIMITE_INICIAL) : filtrados


        listaEl.innerHTML = visiveis.length
            ? visiveis.map(cardMedico).join('')
            : `<p class="doctors-empty">
                   <i class="bi bi-search"></i>
                   Nenhum médico encontrado
               </p>`


        if (contadorEl) {
            contadorEl.textContent =
                filtrados.length + (filtrados.length === 1 ? ' médico' : ' médicos')
        }

        if (verMaisBtn) {
            verMaisBtn.hidden = !(limitar && filtrados.length > LIMITE_INICIAL)
        }

    }


    categorias.forEach(categoria => {

        categoria.addEventListener('click', () => {

            categorias.forEach(item => {
                item.classList.remove('active')
                item.setAttribute('aria-pressed', 'false')
            })

            categoria.classList.add('active')
            categoria.setAttribute('aria-pressed', 'true')

            categoriaAtiva = categoria.dataset.category
            mostrarTodos = false

            aplicarFiltros()

        })

    })


    if (buscaInput) {
        buscaInput.addEventListener('input', aplicarFiltros)
    }


    if (verMaisBtn) {
        verMaisBtn.addEventListener('click', () => {
            mostrarTodos = true
            aplicarFiltros()
        })
    }


    // favoritar sem navegar para agendar.html (delegação: a lista é dinâmica)
    if (listaEl) {

        listaEl.addEventListener('click', event => {

            const botao = event.target.closest('.heart-btn')

            if (!botao) return

            event.preventDefault()
            event.stopPropagation()

            const icon = botao.querySelector('i')

            if (icon) {
                icon.classList.toggle('bi-heart')
                icon.classList.toggle('bi-heart-fill')
                botao.classList.toggle('is-fav', icon.classList.contains('bi-heart-fill'))
            }

        })

    }


    aplicarFiltros()

})()
