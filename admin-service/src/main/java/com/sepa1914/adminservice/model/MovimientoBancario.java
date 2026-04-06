package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "movimientos_bancarios")
public class MovimientoBancario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fechaOperacion;

    @Column(nullable = false)
    private LocalDate fechaValor;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;

    @Column(length = 1)
    private String signo; // 1 = Debe (Gasto), 2 = Haber (Ingreso)

    @Column(length = 500)
    private String concepto; // Aquí concatenaremos todas las líneas de texto del banco

    private String documentoExtra; // Referencia del banco

    private boolean conciliado = false; // ¿Ya lo hemos casado con un recibo?

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunidad_id")
    private Comunidad comunidad;

    public MovimientoBancario() {}

    // --- GETTERS Y SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getFechaOperacion() { return fechaOperacion; }
    public void setFechaOperacion(LocalDate fechaOperacion) { this.fechaOperacion = fechaOperacion; }
    public LocalDate getFechaValor() { return fechaValor; }
    public void setFechaValor(LocalDate fechaValor) { this.fechaValor = fechaValor; }
    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }
    public String getSigno() { return signo; }
    public void setSigno(String signo) { this.signo = signo; }
    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }
    public String getDocumentoExtra() { return documentoExtra; }
    public void setDocumentoExtra(String documentoExtra) { this.documentoExtra = documentoExtra; }
    public boolean isConciliado() { return conciliado; }
    public void setConciliado(boolean conciliado) { this.conciliado = conciliado; }
    public Comunidad getComunidad() { return comunidad; }
    public void setComunidad(Comunidad comunidad) { this.comunidad = comunidad; }
}