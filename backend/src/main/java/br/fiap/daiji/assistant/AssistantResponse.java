package br.fiap.daiji.assistant;

import br.fiap.daiji.dto.CheckinResumo;
import java.math.BigDecimal;
import java.util.List;

/** Resultados mínimos, independentes do transporte e disponíveis para o futuro CP08. */
public sealed interface AssistantResponse {
    record Text(String text) implements AssistantResponse {}
    record Score(String nome, BigDecimal valor, String classificacao) implements AssistantResponse {}
    record ScoreExplanation(Score score, String paragraph) implements AssistantResponse {}
    record Medicamento(String nome, String dosagem, String horario) {}
    record MedicacoesComIds(List<br.fiap.daiji.dto.MedicacaoResponse> itens) implements AssistantResponse {
        public MedicacoesComIds { itens = List.copyOf(itens); }
    }
    record Medicacoes(List<Medicamento> itens) implements AssistantResponse {
        public Medicacoes { itens = List.copyOf(itens); }
    }
    record Checkins(List<CheckinResumo> itens) implements AssistantResponse {
        public Checkins { itens = List.copyOf(itens); }
    }
}
