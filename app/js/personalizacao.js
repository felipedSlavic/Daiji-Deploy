(function () {
    if (!DaijiSession.validar()) return
const formularioPersonalizacao =
    document.querySelector('#personalizacaoForm')


formularioPersonalizacao.addEventListener('submit', function (event) {

    event.preventDefault()


    if (!formularioPersonalizacao.checkValidity()) {

        formularioPersonalizacao.reportValidity()

        return

    }


    window.location.href = 'score.html'

})
})()
