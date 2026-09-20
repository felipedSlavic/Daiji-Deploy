package br.fiap.daiji.controller;

import br.fiap.daiji.dto.*;
import br.fiap.daiji.service.ConfirmacaoMedicacaoService;
import java.sql.SQLException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/beneficiarios/{idBeneficiario}/medicacoes/{idMedicacao}/confirmacoes")
public class ConfirmacaoMedicacaoController {
    private final ConfirmacaoMedicacaoService service;
    public ConfirmacaoMedicacaoController(ConfirmacaoMedicacaoService service) { this.service = service; }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConfirmacaoMedicacaoResponse criar(@PathVariable Integer idBeneficiario, @PathVariable Integer idMedicacao,
                                              @RequestBody ConfirmacaoMedicacaoRequest request) throws SQLException {
        return service.criar(idBeneficiario, idMedicacao, request);
    }
}
