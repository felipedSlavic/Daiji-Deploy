package br.fiap.daiji.controller;
import br.fiap.daiji.dto.*;
import br.fiap.daiji.service.CadastroService;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.SQLException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CadastroController {
    private final CadastroService service;
    public CadastroController(CadastroService service) { this.service=service; }
    @PostMapping("/api/auth/cadastro")
    public ResponseEntity<LoginResponse> cadastrar(@RequestBody JsonNode body) throws SQLException {
        return ResponseEntity.status(201).body(service.cadastrar(CadastroRequest.from(body)));
    }
}
