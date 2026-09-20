package br.fiap.daiji.assistant;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.util.*;

/** Saída do modelo é dado não confiável, inclusive quando segue JSON Schema. */
public final class GeminiOutputValidator {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public record Interpretation(MessageIntent intent, Integer idBeneficiario) {}
    public static final List<String> EXPLANATION_SENTENCES = List.of(
            "Este é o resultado demonstrativo registrado pelo Daiji, calculado pelas regras do sistema.",
            "A classificação acompanha o score salvo e não representa um diagnóstico.",
            "Os fatores usados naquele cálculo não foram armazenados; por isso, não é possível atribuir causas específicas a esse resultado.",
            "Esse resultado é uma referência educacional do MVP, sem validação clínica.");

    private GeminiOutputValidator() {}
    public static JsonNode object(String raw, Set<String> fields) {
        try {
            if (raw == null || raw.length() > 6000) throw new IllegalArgumentException();
            JsonNode value = JSON.readTree(raw);
            if (value == null || !value.isObject() || value.size() != fields.size()) throw new IllegalArgumentException();
            for (String field : fields) if (!value.has(field)) throw new IllegalArgumentException();
            return value;
        } catch (Exception e) { throw new IllegalArgumentException("Resposta estruturada inválida."); }
    }
    public static Interpretation interpretation(String raw) {
        JsonNode value = object(raw, Set.of("intent", "idBeneficiario"));
        if (!value.get("intent").isTextual()) throw new IllegalArgumentException("Intenção inválida.");
        MessageIntent intent;
        try { intent = MessageIntent.valueOf(value.get("intent").textValue()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Intenção inválida."); }
        JsonNode id = value.get("idBeneficiario");
        if (!id.isNull() && (!id.isIntegralNumber() || !id.canConvertToInt() || id.intValue() < 1))
            throw new IllegalArgumentException("ID inválido.");
        if (!intent.requiresId() && !id.isNull()) throw new IllegalArgumentException("Resposta inconsistente.");
        return new Interpretation(intent, id.isNull() ? null : id.intValue());
    }
    public static String explanation(String raw) {
        JsonNode parts = object(raw, Set.of("frases")).get("frases");
        if (!parts.isArray() || parts.isEmpty() || parts.size() > 3) throw new IllegalArgumentException("Explicação inválida.");
        Set<String> selected = new LinkedHashSet<>();
        for (JsonNode part : parts) {
            if (!part.isTextual() || !EXPLANATION_SENTENCES.contains(part.textValue()) || !selected.add(part.textValue()))
                throw new IllegalArgumentException("Explicação inválida.");
        }
        return String.join(" ", selected);
    }
}
