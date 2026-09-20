package br.fiap.daiji.controller;

import br.fiap.daiji.dto.ScoreResponse;
import br.fiap.daiji.service.ScoreService;
import java.sql.SQLException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/beneficiarios/{idBeneficiario}/score")
public class ScoreController {
    private final ScoreService service;
    public ScoreController(ScoreService service) { this.service = service; }

    @PostMapping("/recalcular")
    @ResponseStatus(HttpStatus.CREATED)
    public ScoreResponse recalcular(@PathVariable Integer idBeneficiario) throws SQLException {
        return service.recalcular(idBeneficiario);
    }

    @GetMapping
    public ScoreResponse atual(@PathVariable Integer idBeneficiario) throws SQLException {
        return service.atual(idBeneficiario);
    }
}
