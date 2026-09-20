package br.fiap.daiji.assistant;

import br.fiap.daiji.dao.*;
import br.fiap.daiji.dto.ScoreResponse;
import br.fiap.daiji.gemini.GeminiClient;
import br.fiap.daiji.model.Beneficiario;
import br.fiap.daiji.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScoreExplanationReadOnlyTest {
    @Test void serviceRealConsultaPersistidoSemCriarScoreOuReconstruirCausas() throws Exception {
        BeneficiarioDAO pessoas = mock(BeneficiarioDAO.class); ScoreDAO scores = mock(ScoreDAO.class);
        GeminiClient gemini = mock(GeminiClient.class);
        Beneficiario pessoa = new Beneficiario(); pessoa.setNome("Pessoa");
        when(pessoas.buscarPorId(1)).thenReturn(Optional.of(pessoa));
        when(scores.atual(1)).thenReturn(Optional.of(new ScoreResponse(5,1,null,BigDecimal.valueOf(97),"BAIXO",null)));
        when(gemini.interpret(anyString())).thenReturn("{\"intent\":\"EXPLICAR_SCORE\",\"idBeneficiario\":1}");
        when(gemini.explain(any())).thenReturn("{\"frases\":[\"" + GeminiOutputValidator.EXPLANATION_SENTENCES.get(2) + "\"]}");
        var handler = new ScoreCommandHandler(new BeneficiarioService(pessoas),new ScoreService(pessoas,scores));
        var assistant = new NaturalLanguageAssistant(Optional.of(gemini),List.of(handler));
        var result = assertInstanceOf(AssistantResponse.ScoreExplanation.class,assistant.answer("Explique o score do ID 1"));
        assertEquals(BigDecimal.valueOf(97),result.score().valor());
        verify(scores).atual(1); verifyNoMoreInteractions(scores);
        verify(gemini).explain(new ScoreFacts(BigDecimal.valueOf(97),"BAIXO",false));
    }
}
