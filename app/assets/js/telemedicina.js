document.addEventListener("DOMContentLoaded", () => {

    /* ------------------------------------------------------------------ */
    /* 1) Personalização da página pela especialidade vinda na URL         */
    /* ------------------------------------------------------------------ */

    const parametros = new URLSearchParams(window.location.search)

    const codigoEspecialidade =
        parametros.get("especialidade")


    const especialidades = {

        "clinica-geral": {
            nome: "Clínica Geral",
            descricao:
                "Continue sua jornada com atendimento online em Clínica Geral."
        },

        "psicologia": {
            nome: "Psicologia",
            descricao:
                "Continue sua jornada com atendimento online em Psicologia."
        },

        "nutricao": {
            nome: "Nutrição",
            descricao:
                "Continue sua jornada com atendimento online em Nutrição."
        },

        "cardiologia": {
            nome: "Cardiologia",
            descricao:
                "Continue sua jornada com atendimento online em Cardiologia."
        },

        "dermatologia": {
            nome: "Dermatologia",
            descricao:
                "Continue sua jornada com atendimento online em Dermatologia."
        },

        "ginecologia": {
            nome: "Ginecologia",
            descricao:
                "Continue sua jornada com atendimento online em Ginecologia."
        }

    }


    if (codigoEspecialidade) {

        const especialidade =
            especialidades[codigoEspecialidade]


        if (especialidade) {

            const selectedSpecialtyBox =
                document.getElementById("selectedSpecialtyBox")

            const selectedSpecialty =
                document.getElementById("selectedSpecialty")

            const telemedicineTitle =
                document.getElementById("telemedicineTitle")

            const telemedicineDescription =
                document.getElementById("telemedicineDescription")

            const cardSpecialty =
                document.getElementById("cardSpecialty")


            if (selectedSpecialtyBox) {
                selectedSpecialtyBox.hidden = false
            }

            if (selectedSpecialty) {
                selectedSpecialty.textContent = especialidade.nome
            }

            if (telemedicineTitle) {
                telemedicineTitle.innerHTML =
                    `${especialidade.nome}<br>
                     <em>onde você estiver.</em>`
            }

            if (telemedicineDescription) {
                telemedicineDescription.textContent = especialidade.descricao
            }

            if (cardSpecialty) {
                cardSpecialty.textContent = especialidade.nome
            }

        }

    }


    /* ------------------------------------------------------------------ */
    /* 2) Portão de login: só acessa o Dr.Online logado na Daiji           */
    /* ------------------------------------------------------------------ */

    function daijiEstaLogado() {
        try {
            return localStorage.getItem("daijiLogado") === "true"
        } catch (erro) {
            return false
        }
    }


    document.querySelectorAll("[data-dronline]").forEach((link) => {

        link.addEventListener("click", (evento) => {

            // já logado → deixa o link seguir normalmente para o Dr.Online
            if (daijiEstaLogado()) {
                return
            }

            // não logado → intercepta e manda para o login, guardando o destino
            evento.preventDefault()

            const destino = link.getAttribute("href")

            window.location.href =
                "../app/login.html?redirect=" + encodeURIComponent(destino)

        })

    })

})