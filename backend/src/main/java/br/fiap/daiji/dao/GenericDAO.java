package br.fiap.daiji.dao;

import java.util.List;

public interface GenericDAO<T,id> {
    public abstract void inserir(T entidade);
    public abstract List<T> listar();
}
