package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "remesa_lineas")
public class RemesaLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="remesa_id", nullable=false)
    private Long remesaId;

    @Column(name="vecino_id", nullable=false)
    private Long vecinoId;

    @Column(name="mandato_id")
    private Long mandatoId;

    @Column(name="recibo_contable_id")
    private Long reciboContableId;

    @Column(name="recibo_sepa_id")
    private Long reciboSepaId;

    @Column(nullable=false, precision=11, scale=2)
    private BigDecimal importe;

    @Column(nullable=false, length=140)
    private String concepto;

    @Column(nullable=false)
    private Boolean domiciliado;

    @Column(name="incluido_sepa", nullable=false)
    private Boolean incluidoSepa = false;

    @Column(name="asiento_generado", nullable=false)
    private Boolean asientoGenerado = false;

    @Column(name="pdf_generado", nullable=false)
    private Boolean pdfGenerado = false;

    @Column(name="email_enviado", nullable=false)
    private Boolean emailEnviado = false;

    @Column(name="error_email", columnDefinition="TEXT")
    private String errorEmail;

    // getters y
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRemesaId() {
        return remesaId;
    }

    public void setRemesaId(Long remesaId) {
        this.remesaId = remesaId;
    }

    public Long getVecinoId() {
        return vecinoId;
    }

    public void setVecinoId(Long vecinoId) {
        this.vecinoId = vecinoId;
    }

    public Long getMandatoId() {
        return mandatoId;
    }

    public void setMandatoId(Long mandatoId) {
        this.mandatoId = mandatoId;
    }

    public Long getReciboContableId() {
        return reciboContableId;
    }

    public void setReciboContableId(Long reciboContableId) {
        this.reciboContableId = reciboContableId;
    }

    public Long getReciboSepaId() {
        return reciboSepaId;
    }

    public void setReciboSepaId(Long reciboSepaId) {
        this.reciboSepaId = reciboSepaId;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public void setImporte(BigDecimal importe) {
        this.importe = importe;
    }

    public String getConcepto() {
        return concepto;
    }

    public void setConcepto(String concepto) {
        this.concepto = concepto;
    }

    public Boolean getDomiciliado() {
        return domiciliado;
    }

    public void setDomiciliado(Boolean domiciliado) {
        this.domiciliado = domiciliado;
    }

    public Boolean getIncluidoSepa() {
        return incluidoSepa;
    }

    public void setIncluidoSepa(Boolean incluidoSepa) {
        this.incluidoSepa = incluidoSepa;
    }

    public Boolean getAsientoGenerado() {
        return asientoGenerado;
    }

    public void setAsientoGenerado(Boolean asientoGenerado) {
        this.asientoGenerado = asientoGenerado;
    }

    public Boolean getPdfGenerado() {
        return pdfGenerado;
    }

    public void setPdfGenerado(Boolean pdfGenerado) {
        this.pdfGenerado = pdfGenerado;
    }

    public Boolean getEmailEnviado() {
        return emailEnviado;
    }

    public void setEmailEnviado(Boolean emailEnviado) {
        this.emailEnviado = emailEnviado;
    }

    public String getErrorEmail() {
        return errorEmail;
    }

    public void setErrorEmail(String errorEmail) {
        this.errorEmail = errorEmail;
    }
}