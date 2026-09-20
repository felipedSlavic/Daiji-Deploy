package br.fiap.daiji.assistant;

import br.fiap.daiji.model.ConfirmacaoStatus;

public sealed interface JourneyDecision {
    record Start(int beneficiario) implements JourneyDecision {}
    record Confirm(int beneficiario, int medicacao, ConfirmacaoStatus status) implements JourneyDecision {}
    record Reply(String text) implements JourneyDecision {}
}
