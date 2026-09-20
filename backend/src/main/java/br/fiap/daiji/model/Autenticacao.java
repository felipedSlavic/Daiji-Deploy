package br.fiap.daiji.model;

public record Autenticacao(Integer idAutenticacao, Integer idBeneficiario,
                           String email, String senhaHash, String tipoUsuario, String ativo) {
    @Override
    public String toString() {
        return "Autenticacao[idAutenticacao=" + idAutenticacao + "]";
    }
}
