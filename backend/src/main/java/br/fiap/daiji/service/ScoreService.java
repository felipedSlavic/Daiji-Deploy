package br.fiap.daiji.service;

import br.fiap.daiji.dao.BeneficiarioDAO;
import br.fiap.daiji.dao.ScoreDAO;
import br.fiap.daiji.dto.ScoreResponse;
import br.fiap.daiji.dto.ScoreResponse.Fatores;
import br.fiap.daiji.model.Beneficiario;
import br.fiap.daiji.model.ScoreDados;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Score demonstrativo acadêmico v1.0. Não é escore clínico validado nem diagnóstico. */
@Service
public class ScoreService {
    private final BeneficiarioDAO beneficiarios;
    private final ScoreDAO scores;
    private final Clock clock;
    private static final Set<String> HIPERTENSAO = Set.of("HIPERTENSAO", "HIPERTENSAO ARTERIAL", "HIPERTENSAO ARTERIAL SISTEMICA", "HAS");
    private static final Set<String> DIABETES = Set.of("DIABETES", "DIABETES MELLITUS", "DIABETES TIPO 1", "DIABETES TIPO 2", "DIABETES MELLITUS TIPO 1", "DIABETES MELLITUS TIPO 2", "DM", "DM1", "DM2");

    @Autowired
    public ScoreService(BeneficiarioDAO beneficiarios, ScoreDAO scores) {
        this(beneficiarios, scores, Clock.system(ZoneId.of("America/Sao_Paulo")));
    }

    public ScoreService(BeneficiarioDAO beneficiarios, ScoreDAO scores, Clock clock) {
        this.beneficiarios = beneficiarios;
        this.scores = scores;
        this.clock = clock;
    }

    public ScoreResponse recalcular(Integer id) throws SQLException {
        Beneficiario pessoa = beneficiario(id);
        LocalDateTime momento = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Fatores fatores = calcular(pessoa.getDataNascimento(), momento.toLocalDate(),
                scores.comorbidades(id), scores.adesao(id), scores.ultimosCheckins(id));
        int valor = Math.clamp(100 - fatores.total(), 0, 100);
        return scores.criar(new ScoreResponse(0, id, momento, BigDecimal.valueOf(valor).setScale(2),
                classificar(valor), fatores));
    }

    public ScoreResponse atual(Integer id) throws SQLException {
        beneficiario(id);
        return scores.atual(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Score não encontrado."));
    }

    private Beneficiario beneficiario(Integer id) throws SQLException {
        if (id == null || id < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Beneficiário inválido.");
        return beneficiarios.buscarPorId(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Beneficiário não encontrado."));
    }

    static String normalizar(String texto) {
        return texto == null ? "" : Normalizer.normalize(texto.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static Fatores calcular(LocalDate nascimento, LocalDate referencia, List<String> comorbidades,
                                   ScoreDados.Adesao adesao, List<ScoreDados.Checkin> checkins) {
        Set<String> distintas = new HashSet<>();
        comorbidades.stream().map(ScoreService::normalizar).filter(s -> !s.isEmpty()).forEach(distintas::add);
        int hipertensao = distintas.stream().anyMatch(HIPERTENSAO::contains) ? 10 : 0;
        int diabetes = distintas.stream().anyMatch(DIABETES::contains) ? 10 : 0;
        distintas.removeAll(HIPERTENSAO);
        distintas.removeAll(DIABETES);
        int idade = nascimento == null ? 0 : Period.between(nascimento, referencia).getYears();
        int penalidadeIdade = idade >= 70 ? 10 : idade >= 60 ? 5 : idade >= 40 ? 3 : 0;
        // Comparações exatas, sem arredondar percentuais antes de classificar.
        BigDecimal confirmados = BigDecimal.valueOf(adesao.confirmados());
        BigDecimal total = confirmados.add(BigDecimal.valueOf(adesao.perdidos()));
        BigDecimal percentual = confirmados.multiply(BigDecimal.valueOf(100));
        boolean temAdesao = total.signum() > 0;
        int penalidadeAdesao = !temAdesao || percentual.compareTo(total.multiply(BigDecimal.valueOf(90))) >= 0 ? 0
                : percentual.compareTo(total.multiply(BigDecimal.valueOf(75))) >= 0 ? 5
                : percentual.compareTo(total.multiply(BigDecimal.valueOf(50))) >= 0 ? 10 : 15;
        int pontos = 0, maximo = 0;
        // DAO entrega os mais recentes primeiro, incluindo desempate pelo ID.
        for (ScoreDados.Checkin c : checkins.stream().limit(7).toList()) {
            if (c.estresse() != null && c.estresse() >= 1 && c.estresse() <= 5) {
                pontos += Math.max(0, c.estresse() - 2);
                maximo += 3;
            }
            for (int componente : new int[]{qualidade(c.sono()), qualidade(c.alimentacao()), humor(c.humor())}) {
                if (componente >= 0) { pontos += componente; maximo += 2; }
            }
        }
        int penalidadeCheckins = maximo == 0 || pontos * 4 <= maximo ? 0
                : pontos * 2 <= maximo ? 5 : pontos * 4 <= maximo * 3 ? 10 : 15;
        return new Fatores(hipertensao, diabetes, Math.min(3, distintas.size()) * 5, penalidadeIdade,
                penalidadeAdesao, penalidadeCheckins, temAdesao, maximo > 0);
    }

    private static int qualidade(String texto) {
        return switch (normalizar(texto)) { case "BOA", "BOM" -> 0; case "REGULAR" -> 1; case "RUIM" -> 2; default -> -1; };
    }

    private static int humor(String texto) {
        return switch (normalizar(texto)) {
            case "CALMO", "BEM", "BOM", "FELIZ", "POSITIVO" -> 0;
            case "REGULAR", "NEUTRO" -> 1;
            case "TRISTE", "ANSIOSO", "ESTRESSADO", "IRRITADO", "RUIM", "NEGATIVO" -> 2;
            default -> -1;
        };
    }

    public static String classificar(double valor) { return valor >= 70 ? "BAIXO" : valor >= 40 ? "MEDIO" : "ALTO"; }
}
