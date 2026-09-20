package br.fiap.daiji.model;

/** Somente dados estruturados usados pelo score demonstrativo v1. */
public final class ScoreDados {
    private ScoreDados() { }
    public record Adesao(long confirmados, long perdidos) { }
    public record Checkin(Integer estresse, String sono, String alimentacao, String humor) { }
}
