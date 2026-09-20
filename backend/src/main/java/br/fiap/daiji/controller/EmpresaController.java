package br.fiap.daiji.controller;
import br.fiap.daiji.dao.EmpresaOpcaoDAO;
import br.fiap.daiji.dto.EmpresaOpcaoResponse;
import java.sql.SQLException;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class EmpresaController {
    private final EmpresaOpcaoDAO dao;
    public EmpresaController(EmpresaOpcaoDAO dao) { this.dao=dao; }
    @GetMapping("/api/empresas")
    public List<EmpresaOpcaoResponse> listar() throws SQLException { return dao.listar(); }
}
