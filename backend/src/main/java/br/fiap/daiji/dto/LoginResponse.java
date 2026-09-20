package br.fiap.daiji.dto;

public record LoginResponse(Integer idAutenticacao, Integer idBeneficiario,
                            String nome, String email, String tipoUsuario) {
}
