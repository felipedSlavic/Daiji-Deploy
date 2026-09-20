package br.fiap.daiji.assistant;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Triagem conservadora, anterior a qualquer envio ao provedor. Não é autenticação. */
public final class AssistantSafety {
    private AssistantSafety() {}
    private static final Pattern ID = Pattern.compile("\\b(?:beneficiario|id)\\s*(?:[:#]\\s*)?([+-]?\\d+)(?![\\p{L}\\d]|[.,]\\d)");
    private static final Pattern RESTRICTED = Pattern.compile(
            "\\b(cpf|email|e-mail|telefone|celular|empresa|senha|password|credencia\\w*|token|api.?key|chave|"
            + "gemini_api_key|telegram_bot_token|oracle|jdbc|chat.?id|sql|select|insert|update|delete|drop|"
            + "prompt|system|sistema|ignore|ignorar|desconsidere|instruc\\w*|regras|execute|executar|"
            + "diagnostic\\w*|diagnost\\w*|prescre\\w*|receit\\w*|dose|dosagem|tomar|tomo|parar|pare|"
            + "interromp\\w*|suspender|suspenda|aumentar|aumente|diminuir|diminua|reduzir|reduza|"
            + "tratamento|sintoma\\w*|dor|doenca\\w*|renal|cura|curar)\\b");
    private static final Pattern PRIVATE_DATA = Pattern.compile("@|https?://|\\b\\d{3}[. -]\\d{3}[. -]\\d{3}[- ]\\d{2}\\b|"
            + "\\b\\d{8,}\\b|(?:\\+?\\d[ ()-]*){10,}|[A-Za-z0-9_/-]{32,}");

    public static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).strip();
    }
    public static boolean blocked(String text) {
        String normalized = normalize(text);
        // Remover somente IDs válidos rotulados antes de verificar sequências numéricas pessoais.
        String withoutId = ID.matcher(normalized).replaceAll("beneficiario");
        return text == null || text.length() > 600 || RESTRICTED.matcher(normalized).find()
                || PRIVATE_DATA.matcher(withoutId).find() || normalized.contains("\\")
                || normalized.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t');
    }
    /** Ausência: null. Ambiguidade/formato inválido: exceção sem ecoar a entrada. */
    public static Integer explicitId(String text) {
        String normalized = normalize(text);
        var matcher = ID.matcher(normalized);
        Set<Integer> values = new HashSet<>();
        int matches = 0;
        while (matcher.find()) {
            matches++;
            String value = matcher.group(1);
            if (!value.matches("[0-9]+")) throw new IllegalArgumentException("ID inválido.");
            int parsed;
            try { parsed = Integer.parseInt(value); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("ID inválido."); }
            if (parsed < 1) throw new IllegalArgumentException("ID inválido.");
            values.add(parsed);
        }
        long labels = Pattern.compile("\\b(?:beneficiario|id)\\b").matcher(normalized).results().count();
        if (values.size() > 1 || (labels > 0 && matches != labels)) throw new IllegalArgumentException("ID inválido.");
        // Mais de um número na solicitação é ambíguo (comparação, segunda pessoa, idade etc.).
        String remainder = ID.matcher(normalized).replaceAll("");
        if (Pattern.compile("\\d").matcher(remainder).find()) throw new IllegalArgumentException("ID inválido.");
        return values.isEmpty() ? null : values.iterator().next();
    }
}
