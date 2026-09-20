package br.fiap.daiji.service;

import br.fiap.daiji.dao.GestaoDAO;
import br.fiap.daiji.dao.ProfissionalDAO;
import br.fiap.daiji.dto.*;
import java.sql.SQLException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GestaoService {
    private final GestaoDAO gestao;
    private final ProfissionalDAO profissionais;

    public GestaoService(GestaoDAO gestao, ProfissionalDAO profissionais) {
        this.gestao = gestao;
        this.profissionais = profissionais;
    }

    public void validarEmpresa(Integer empresa) throws SQLException {
        if (empresa == null) return;
        if (empresa < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empresa inválida.");
        if (!gestao.empresaExiste(empresa))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada.");
    }

    public DashboardResponse dashboard(Integer empresa) throws SQLException {
        validarEmpresa(empresa);
        return gestao.dashboard(empresa);
    }

    public List<GestaoBeneficiarioResponse> beneficiarios(Integer empresa, boolean somenteAtencao) throws SQLException {
        validarEmpresa(empresa);
        return gestao.beneficiarios(empresa, somenteAtencao);
    }

    public List<ProfissionalResponse> profissionais() throws SQLException { return profissionais.listar(); }
}
