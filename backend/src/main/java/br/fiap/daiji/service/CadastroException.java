package br.fiap.daiji.service;
public class CadastroException extends RuntimeException {
    private final int status;
    public CadastroException(int status,String mensagem) { super(mensagem); this.status=status; }
    public int status() { return status; }
    public static CadastroException invalido() { return new CadastroException(400,"Dados de cadastro inválidos."); }
}
