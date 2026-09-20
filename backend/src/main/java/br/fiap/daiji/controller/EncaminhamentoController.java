package br.fiap.daiji.controller;

import br.fiap.daiji.dto.*;
import br.fiap.daiji.service.EncaminhamentoService;
import java.sql.SQLException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.web.bind.WebDataBinder;

@RestController
@RequestMapping("/api/gestao")
public class EncaminhamentoController {
    private final EncaminhamentoService service;
    public EncaminhamentoController(EncaminhamentoService service) { this.service = service; }

    @PostMapping("/beneficiarios/{idBeneficiario}/encaminhamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public EncaminhamentoResponse criar(@PathVariable Integer idBeneficiario,
                                         @RequestBody EncaminhamentoRequest request) throws SQLException {
        return service.criar(idBeneficiario, request);
    }

    // Parâmetro informado vazio é inválido; somente a ausência significa visão global.
    @InitBinder
    public void configurarNumeros(WebDataBinder binder) {
        binder.registerCustomEditor(Integer.class, new CustomNumberEditor(Integer.class, false));
    }
    @GetMapping("/encaminhamentos")
    public List<EncaminhamentoResponse> listar(@RequestParam(required = false) Integer idEmpresa,
                                               @RequestParam(required = false) String status) throws SQLException {
        return service.listar(idEmpresa, status);
    }

    @PatchMapping("/encaminhamentos/{idEncaminhamento}")
    public EncaminhamentoResponse atualizar(@PathVariable Long idEncaminhamento,
                                             @RequestBody EncaminhamentoUpdateRequest request) throws SQLException {
        return service.atualizar(idEncaminhamento, request);
    }
}

