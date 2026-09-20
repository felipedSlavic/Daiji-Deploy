package br.fiap.daiji.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Checkin {
    private Integer id;
    private Beneficiario beneficiario;
    private LocalDateTime dataCheckin;
    private String canal;
    private Integer nivelEstresse;
    private String qualidadeSono;
    private String qualidadeAlimentacao;
    private String humor;
    private String respostaTexto;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Beneficiario getBeneficiario() {
        return beneficiario;
    }

    public void setBeneficiario(Beneficiario beneficiario) {
        this.beneficiario = beneficiario;
    }

    public LocalDateTime getDataCheckin() {
        return dataCheckin;
    }

    public void setDataCheckin(LocalDateTime dataCheckin) {
        this.dataCheckin = dataCheckin;
    }

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal;
    }

    public Integer getNivelEstresse() {
        return nivelEstresse;
    }

    public void setNivelEstresse(Integer nivelEstresse) {
        this.nivelEstresse = nivelEstresse;
    }

    public String getQualidadeSono() {
        return qualidadeSono;
    }

    public void setQualidadeSono(String qualidadeSono) {
        this.qualidadeSono = qualidadeSono;
    }

    public String getQualidadeAlimentacao() {
        return qualidadeAlimentacao;
    }

    public void setQualidadeAlimentacao(String qualidadeAlimentacao) {
        this.qualidadeAlimentacao = qualidadeAlimentacao;
    }

    public String getHumor() {
        return humor;
    }

    public void setHumor(String humor) {
        this.humor = humor;
    }

    public String getRespostaTexto() {
        return respostaTexto;
    }

    public void setRespostaTexto(String respostaTexto) {
        this.respostaTexto = respostaTexto;
    }
}
