package br.fiap.daiji.controller;

import br.fiap.daiji.dto.BeneficiarioResponse;
import br.fiap.daiji.service.BeneficiarioService;
import java.sql.SQLException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/beneficiarios")
public class BeneficiarioController {
    private final BeneficiarioService beneficiarioService;

    public BeneficiarioController(BeneficiarioService beneficiarioService) {
        this.beneficiarioService = beneficiarioService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<BeneficiarioResponse> buscarPorId(@PathVariable Integer id) throws SQLException {
        return beneficiarioService.buscarPorId(id)
                .map(BeneficiarioResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
