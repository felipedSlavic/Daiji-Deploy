package br.fiap.daiji.dto;

import br.fiap.daiji.model.Beneficiario;
import java.time.LocalDate;

public record BeneficiarioResponse(
        Integer id, Integer idEmpresa, String nome, String cpf, String email,
        LocalDate dataNascimento, String telefone, String status, LocalDate dataCadastro) {
    public static BeneficiarioResponse from(Beneficiario beneficiario) {
        return new BeneficiarioResponse(
                beneficiario.getId(), beneficiario.getEmpresa().getId(),
                beneficiario.getNome(), beneficiario.getCpf(), beneficiario.getEmail(),
                beneficiario.getDataNascimento(), beneficiario.getTelefone(),
                beneficiario.getStatus(), beneficiario.getDataCadastro());
    }
}
