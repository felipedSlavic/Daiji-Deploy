const formularioPersonalizacao =
    document.querySelector('#personalizacaoForm')


formularioPersonalizacao.addEventListener('submit', function (event) {

    event.preventDefault()


    if (!formularioPersonalizacao.checkValidity()) {

        formularioPersonalizacao.reportValidity()

        return

    }


    try {

        const diabetes =
            formularioPersonalizacao.querySelector(
                'input[name="diabetes"]:checked'
            )

        localStorage.setItem(
            'daiji_diabetes',
            diabetes ?
                diabetes.value :
                'nao'
        )

    }

    catch (error) {

        console.log(
            'Não foi possível salvar a preferência de diabetes'
        )

    }


    window.location.href = 'score.html'

})
