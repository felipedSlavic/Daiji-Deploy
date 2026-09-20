package br.fiap.daiji.controller;
import br.fiap.daiji.service.CadastroException;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;

@Order(-1)
@RestControllerAdvice(assignableTypes=CadastroController.class)
public class CadastroExceptionHandler {
    @ExceptionHandler(CadastroException.class)
    public ResponseEntity<Map<String,String>> cadastro(CadastroException e) {
        return ResponseEntity.status(e.status()).body(Map.of("mensagem",e.getMessage()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String,String>> entrada() {
        return ResponseEntity.badRequest().body(Map.of("mensagem","Dados de cadastro inválidos."));
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String,String>> tipoDeConteudo() {
        return ResponseEntity.status(415).body(Map.of("mensagem","Envie o cadastro como application/json."));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> interno() {
        return ResponseEntity.internalServerError().body(Map.of("mensagem","Não foi possível concluir o cadastro."));
    }
}
