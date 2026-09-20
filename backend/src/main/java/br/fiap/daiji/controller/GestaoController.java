package br.fiap.daiji.controller;

import br.fiap.daiji.dto.*;
import br.fiap.daiji.service.GestaoService;
import java.sql.SQLException;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.web.bind.WebDataBinder;

@RestController
@RequestMapping("/api/gestao")
public class GestaoController {
    private final GestaoService service;
    public GestaoController(GestaoService service) { this.service = service; }

    // Parâmetro informado vazio é inválido; somente a ausência significa visão global.
    @InitBinder
    public void configurarNumeros(WebDataBinder binder) {
        binder.registerCustomEditor(Integer.class, new CustomNumberEditor(Integer.class, false));
    }
    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@RequestParam(required = false) Integer idEmpresa) throws SQLException {
        return service.dashboard(idEmpresa);
    }

    @GetMapping("/beneficiarios")
    public List<GestaoBeneficiarioResponse> beneficiarios(@RequestParam(required = false) Integer idEmpresa) throws SQLException {
        return service.beneficiarios(idEmpresa, false);
    }

    @GetMapping("/casos-atencao")
    public List<GestaoBeneficiarioResponse> casosAtencao(@RequestParam(required = false) Integer idEmpresa) throws SQLException {
        return service.beneficiarios(idEmpresa, true);
    }

    @GetMapping("/profissionais")
    public List<ProfissionalResponse> profissionais() throws SQLException { return service.profissionais(); }
}

