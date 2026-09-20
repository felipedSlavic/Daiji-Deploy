package br.fiap.daiji.dto;

import java.time.LocalDateTime;

/** Projeção de leitura sem resposta livre ou identificadores pessoais. */
public record CheckinResumo(LocalDateTime data, Integer estresse, String sono,
                            String alimentacao, String humor) {}
