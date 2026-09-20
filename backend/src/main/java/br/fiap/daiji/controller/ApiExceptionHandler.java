package br.fiap.daiji.controller;

import java.sql.SQLException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(br.fiap.daiji.service.CredenciaisInvalidasException.class)
    public ResponseEntity<Map<String, String>> tratarCredenciaisInvalidas() {
        return ResponseEntity.status(401).body(Map.of("mensagem", "Credenciais inválidas."));
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<Map<String, String>> tratarErroBanco(SQLException exception) {
        return ResponseEntity.internalServerError()
                .body(Map.of("mensagem", "Não foi possível consultar o banco de dados."));
    }
}
