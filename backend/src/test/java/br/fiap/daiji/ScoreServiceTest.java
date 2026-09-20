package br.fiap.daiji;

import br.fiap.daiji.dao.*;
import br.fiap.daiji.dto.ScoreResponse;
import br.fiap.daiji.model.*;
import br.fiap.daiji.service.ScoreService;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ScoreServiceTest {
    static final LocalDate DATA = LocalDate.of(2026, 9, 16);
    static final ScoreDados.Adesao SEM_ADESAO = new ScoreDados.Adesao(0, 0);
    static final ScoreDados.Checkin BOM = new ScoreDados.Checkin(1, " bóa ", "BOM", "feliz");
    static final ScoreDados.Checkin RUIM = new ScoreDados.Checkin(5, "RUIM", "RUIM", "TRISTE");

    ScoreResponse.Fatores fatores(List<String> comorbidades) {
        return ScoreService.calcular(DATA.minusYears(20), DATA, comorbidades, SEM_ADESAO, List.of());
    }

    @Test void minimo() {
        var f = fatores(List.of());
        assertEquals(0, f.total());
        assertFalse(f.dadosAdesaoSuficientes());
        assertFalse(f.dadosCheckinsSuficientes());
    }

    @ParameterizedTest @CsvSource({"hipertensão,10,0", " DIABETES ,0,10", "Hipertensão Arterial Sistêmica,10,0", "Diabetes mellitus tipo 2,0,10"})
    void principais(String descricao, int h, int d) {
        var f = fatores(List.of(descricao));
        assertEquals(h, f.hipertensao()); assertEquals(d, f.diabetes()); assertEquals(0, f.outrasComorbidades());
    }

    @Test void duplicatasNormalizadasEAliasesNaoContamComoOutras() {
        var f = fatores(List.of(" hipertensão ", "HIPERTENSAO", "HAS", "DIABETES", "diabetes mellitus", "Outra", " ÓUTRA "));
        assertEquals(10, f.hipertensao()); assertEquals(10, f.diabetes()); assertEquals(5, f.outrasComorbidades());
    }

    @ParameterizedTest @CsvSource({"0,0", "1,5", "2,10", "3,15", "10,15"})
    void tetoOutras(int quantidade, int esperado) {
        assertEquals(esperado, fatores(Stream.iterate(0, n -> n + 1).limit(quantidade).map(n -> "Outra " + n).toList()).outrasComorbidades());
    }

    @ParameterizedTest @CsvSource({"39,0", "40,3", "59,3", "60,5", "69,5", "70,10", "95,10"})
    void faixasIdade(int idade, int esperado) {
        assertEquals(esperado, ScoreService.calcular(DATA.minusYears(idade), DATA, List.of(), SEM_ADESAO, List.of()).idade());
    }

    @Test void vesperaDoAniversario() {
        assertEquals(0, ScoreService.calcular(DATA.minusYears(40).plusDays(1), DATA, List.of(), SEM_ADESAO, List.of()).idade());
    }

    @ParameterizedTest @CsvSource({"100,0,0", "90,10,0", "89999,10001,5", "75,25,5", "74999,25001,10", "50,50,10", "49999,50001,15", "0,1,15", "0,0,0"})
    void adesaoSemArredondamento(long confirmados, long perdidos, int esperado) {
        var f = ScoreService.calcular(DATA, DATA, List.of(), new ScoreDados.Adesao(confirmados, perdidos), List.of());
        assertEquals(esperado, f.adesaoMedicacao());
        assertEquals(confirmados + perdidos > 0, f.dadosAdesaoSuficientes());
    }

    static Stream<Arguments> checkins() {
        return Stream.of(
            Arguments.of(List.of(BOM), 0, true),
            Arguments.of(List.of(RUIM), 15, true),
            Arguments.of(List.of(new ScoreDados.Checkin(3, "REGULAR", "REGULAR", "NEUTRO")), 5, true),
            Arguments.of(List.of(new ScoreDados.Checkin(4, "REGULAR", "RUIM", "NEUTRO")), 10, true),
            Arguments.of(List.of(new ScoreDados.Checkin(null, null, "desconhecido", null)), 0, false),
            Arguments.of(List.of(new ScoreDados.Checkin(null, "RUIM", null, "desconhecido")), 15, true),
            Arguments.of(List.of(new ScoreDados.Checkin(9, null, null, null)), 0, false),
            Arguments.of(List.of(), 0, false),
            Arguments.of(List.of(new ScoreDados.Checkin(null, "REGULAR", "BOM", null)), 0, true),
            Arguments.of(List.of(new ScoreDados.Checkin(null, "RUIM", "BOM", null)), 5, true),
            Arguments.of(List.of(new ScoreDados.Checkin(null, "RUIM", "REGULAR", null)), 10, true),
            Arguments.of(List.of(new ScoreDados.Checkin(null, "RUIM", null, null), BOM), 0, true)
        );
    }

    @ParameterizedTest @MethodSource("checkins")
    void componentesReconhecidosELimites(List<ScoreDados.Checkin> checkins, int esperado, boolean suficiente) {
        var f = ScoreService.calcular(DATA, DATA, List.of(), SEM_ADESAO, checkins);
        assertEquals(esperado, f.checkins()); assertEquals(suficiente, f.dadosCheckinsSuficientes());
    }

    @Test void apenasSeteMaisRecentes() {
        var lista = new ArrayList<>(Collections.nCopies(7, BOM)); lista.add(RUIM);
        assertEquals(0, ScoreService.calcular(DATA, DATA, List.of(), SEM_ADESAO, lista).checkins());
    }

    static Stream<Arguments> fronteirasClassificacao() {
        return Stream.of(
                Arguments.of(100.0, "BAIXO"),
                Arguments.of(70.0, "BAIXO"),
                Arguments.of(Math.nextDown(70.0), "MEDIO"),
                Arguments.of(40.0, "MEDIO"),
                Arguments.of(Math.nextDown(40.0), "ALTO"),
                Arguments.of(0.0, "ALTO"));
    }

    @ParameterizedTest @MethodSource("fronteirasClassificacao")
    void classificacao(double score, String esperada) { assertEquals(esperada, ScoreService.classificar(score)); }

    @Test void calculaPersisteDeterministicamenteEntreZeroECem() throws Exception {
        var beneficiarios = mock(BeneficiarioDAO.class); var dao = mock(ScoreDAO.class);
        var pessoa = new Beneficiario(); pessoa.setDataNascimento(DATA.minusYears(70));
        when(beneficiarios.buscarPorId(7)).thenReturn(Optional.of(pessoa));
        when(dao.criar(any())).thenAnswer(i -> i.getArgument(0));
        when(dao.comorbidades(7)).thenReturn(List.of("HIPERTENSAO", "DIABETES", "A", "B", "C", "D"));
        when(dao.adesao(7)).thenReturn(new ScoreDados.Adesao(0, 5));
        when(dao.ultimosCheckins(7)).thenReturn(Collections.nCopies(7, RUIM));
        var service = new ScoreService(beneficiarios, dao, Clock.fixed(Instant.parse("2026-09-16T15:30:45Z"), ZoneId.of("America/Sao_Paulo")));
        var score = service.recalcular(7);
        assertEquals(25, score.valorScore().intValueExact());
        assertEquals("ALTO", score.classificacaoRisco());
        assertEquals(LocalDateTime.of(2026, 9, 16, 12, 30, 45), score.dataCalculo());
        assertEquals(score, service.recalcular(7));
        verify(dao, times(2)).criar(score);
        pessoa.setDataNascimento(DATA.minusYears(20));
        when(dao.comorbidades(7)).thenReturn(List.of()); when(dao.adesao(7)).thenReturn(SEM_ADESAO);
        when(dao.ultimosCheckins(7)).thenReturn(List.of());
        assertEquals(100, service.recalcular(7).valorScore().intValueExact());
        // Com as regras v1, a soma máxima é 75: o mínimo alcançável é 25.
    }
}
