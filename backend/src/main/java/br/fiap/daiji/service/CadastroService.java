package br.fiap.daiji.service;

import br.fiap.daiji.dao.CadastroDAO;
import br.fiap.daiji.dto.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.sql.SQLException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CadastroService {
    private final CadastroDAO dao;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
    public CadastroService(CadastroDAO dao) { this.dao=dao; }
    public LoginResponse cadastrar(CadastroRequest r) throws SQLException {
        if(r==null || r.idEmpresa()==null || r.idEmpresa()<=0 || !texto(r.nome(),150)
                || r.cpf()==null || !r.cpf().matches("[0-9]{11}")
                || !texto(r.email(),150) || !emailValido(r.email())
                || r.telefoneWhatsapp()==null || !r.telefoneWhatsapp().matches("[0-9]{10,15}")
                || !texto(r.senha(),72) || r.dataNascimento()==null
                || !r.dataNascimento().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw CadastroException.invalido();
        LocalDate data;
        try { data=LocalDate.parse(r.dataNascimento()); }
        catch(DateTimeParseException e) { throw CadastroException.invalido(); }
        if(data.getYear()<1 || data.isAfter(LocalDate.now())) throw CadastroException.invalido();
        return dao.criar(r,data,encoder.encode(r.senha()));
    }
    private static boolean emailValido(String email) {
        return email.matches("[A-Za-z0-9!#$%&'*+/=?^_{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_{|}~-]+)*@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+");
    }
    private static boolean texto(String s,int limite) {
        return s!=null && !s.isBlank() && s.getBytes(StandardCharsets.UTF_8).length<=limite;
    }
}
