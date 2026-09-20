(function () {
    if (!DaijiSession.validar()) return
const categorias = document.querySelectorAll('.category-item')

categorias.forEach(categoria => {

    categoria.addEventListener('click', () => {

        categorias.forEach(item => {

            item.classList.remove('active')

            item.setAttribute('aria-pressed', 'false')

        })

        categoria.classList.add('active')

        categoria.setAttribute('aria-pressed', 'true')

    })

})
})()
