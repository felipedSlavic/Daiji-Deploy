package br.fiap.daiji.controller;

import br.fiap.daiji.dto.MedicacaoRequest;
import br.fiap.daiji.dto.MedicacaoResponse;
import br.fiap.daiji.service.MedicacaoService;
import java.sql.SQLException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/beneficiarios/{idBeneficiario}/medicacoes")
public class MedicacaoController {
    private final MedicacaoService medicacaoService;

    public MedicacaoController(MedicacaoService medicacaoService) {
        this.medicacaoService = medicacaoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MedicacaoResponse criar(@PathVariable Integer idBeneficiario,
                                  @RequestBody MedicacaoRequest request) throws SQLException {
        return medicacaoService.criar(idBeneficiario, request);
    }

    @GetMapping
    public List<MedicacaoResponse> listar(@PathVariable Integer idBeneficiario) throws SQLException {
        return medicacaoService.listar(idBeneficiario);
    }
}
