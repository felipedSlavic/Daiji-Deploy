package br.fiap.daiji.dto;

import com.fasterxml.jackson.databind.JsonNode;
import br.fiap.daiji.service.CadastroException;
import java.util.Set;

public record CadastroRequest(Integer idEmpresa, String nome, String cpf, String email,
                              String dataNascimento, String telefoneWhatsapp, String senha) {
    public static CadastroRequest from(JsonNode json) {
        var campos = Set.of("idEmpresa","nome","cpf","email","dataNascimento","telefoneWhatsapp","senha");
        if (json == null || !json.isObject() || json.size()!=campos.size()) throw CadastroException.invalido();
        json.fieldNames().forEachRemaining(c -> { if (!campos.contains(c)) throw CadastroException.invalido(); });
        JsonNode id=json.get("idEmpresa");
        if(id==null || !id.isIntegralNumber() || !id.canConvertToInt()) throw CadastroException.invalido();
        return new CadastroRequest(id.intValue(),texto(json,"nome"),texto(json,"cpf"),texto(json,"email"),
                texto(json,"dataNascimento"),texto(json,"telefoneWhatsapp"),texto(json,"senha"));
    }
    private static String texto(JsonNode json,String campo) {
        JsonNode value=json.get(campo);
        if(value==null || !value.isTextual()) throw CadastroException.invalido();
        return value.textValue();
    }
    @Override public String toString() { return "CadastroRequest[dados omitidos]"; }
}
