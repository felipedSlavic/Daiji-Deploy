package br.fiap.daiji.dto;

public record LoginRequest(String email, String senha) {
    @Override
    public String toString() {
        return "LoginRequest[credenciais omitidas]";
    }
}
