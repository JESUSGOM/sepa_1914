package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.dto.*;
import com.sepa1914.adminservice.model.*;
import com.sepa1914.adminservice.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.RoundingMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de Contabilidad Integral para SEPA 1914.
 * OPTIMIZACIÓN GTI: Soporte para millones de registros mediante carga en lote.
 */
@Service
public class ContabilidadService {

    private static final Logger log = LoggerFactory.getLogger(ContabilidadService.class);

    private final MovimientoBancarioRepository movRepo;
    private final ReciboRepository reciboRepo;
    private final IncidenciaRepository incidenciaRepo;
    private final ComunidadRepository comunidadRepository;
    private final VecinoRepository vecinoRepository;
    private final CuentaContableRepository cuentaContableRepository;
    private final MovimientoContableRepository movContableRepo;
    private final GastoRepository gastoRepository;
    private final PresupuestoRepository presupuestoRepo;
    private final ConceptoCobroRepository conceptoCobroRepo;

    public ContabilidadService(MovimientoBancarioRepository movRepo,
                               ReciboRepository reciboRepo,
                               IncidenciaRepository incidenciaRepo,
                               ComunidadRepository comunidadRepository,
                               VecinoRepository vecinoRepository,
                               CuentaContableRepository cuentaContableRepository,
                               MovimientoContableRepository movContableRepo,
                               GastoRepository gastoRepository,
                               PresupuestoRepository presupuestoRepo,
                               ConceptoCobroRepository conceptoCobroRepo) {
        this.movRepo = movRepo;
        this.reciboRepo = reciboRepo;
        this.incidenciaRepo = incidenciaRepo;
        this.comunidadRepository = comunidadRepository;
        this.vecinoRepository = vecinoRepository;
        this.cuentaContableRepository = cuentaContableRepository;
        this.movContableRepo = movContableRepo;
        this.gastoRepository = gastoRepository;
        this.presupuestoRepo = presupuestoRepo;
        this.conceptoCobroRepo = conceptoCobroRepo;
    }

    @Transactional(readOnly = true)
    public List<DesviacionPresupuestoDTO> obtenerInformeGastosReal(Long comunidadId, int ejercicio) {
        // 1. CARGA TOTAL RAM (Cero consultas dentro del bucle)
        List<CuentaContable> cuentas = cuentaContableRepository.findByComunidadIdAndTipo(comunidadId, TipoCuenta.GASTO);

        Map<Long, BigDecimal> mP = new HashMap<>();
        presupuestoRepo.findByComunidadIdAndAnio(comunidadId, ejercicio).forEach(p -> mP.put(p.getCuenta().getId(), p.getImporte()));

        Map<Long, BigDecimal[]> mS = new HashMap<>();
        movContableRepo.obtenerTodosLosSaldosAnuales(comunidadId, ejercicio).forEach(s -> mS.put((Long)s[0], new BigDecimal[]{(BigDecimal)s[1], (BigDecimal)s[2]}));

        Map<Long, BigDecimal> mPag = new HashMap<>();
        gastoRepository.sumAllPagadoByComunidadAndAnio(comunidadId, ejercicio).forEach(g -> mPag.put((Long)g[0], (BigDecimal)g[1]));

        // Convertimos la lista a Mapa para evitar accesos Lazy de Hibernate
        Map<Long, CuentaContable> cacheCtas = new HashMap<>();
        cuentas.forEach(c -> cacheCtas.put(c.getId(), c));

        return cuentas.stream().map(c -> {
            CuentaContable cta = cacheCtas.get(c.getId());
            BigDecimal pVal = mP.getOrDefault(cta.getId(), BigDecimal.ZERO);
            BigDecimal[] saldos = mS.getOrDefault(cta.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal pag = mPag.getOrDefault(cta.getId(), BigDecimal.ZERO);

            return new DesviacionPresupuestoDTO(cta.getId(), cta.getCodigo(), cta.getNombre(),
                    pVal, pag, saldos[0], saldos[0].subtract(pag));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<DesviacionPresupuestoDTO> obtenerInformeIngresosReal(Long comunidadId, int ejercicio) {
        List<CuentaContable> cuentas = cuentaContableRepository.findByComunidadIdAndTipo(comunidadId, TipoCuenta.INGRESO);

        Map<Long, BigDecimal> mP = new HashMap<>();
        presupuestoRepo.findByComunidadIdAndAnio(comunidadId, ejercicio).forEach(p -> mP.put(p.getCuenta().getId(), p.getImporte()));

        Map<Long, BigDecimal[]> mS = new HashMap<>();
        movContableRepo.obtenerTodosLosSaldosAnuales(comunidadId, ejercicio).forEach(s -> mS.put((Long)s[0], new BigDecimal[]{(BigDecimal)s[1], (BigDecimal)s[2]}));

        List<ConceptoCobro> genConcepts = conceptoCobroRepo.findAllGenericConcepts();
        Map<Long, List<String>> ctasMap = new HashMap<>();
        genConcepts.stream().filter(gc -> gc.getCuentaContable() != null).forEach(gc ->
                ctasMap.computeIfAbsent(gc.getCuentaContable().getId(), k -> new ArrayList<>()).add(gc.getDescripcion().toLowerCase()));

        List<Recibo> recsAnio = reciboRepo.findByComunidadId(comunidadId).stream()
                .filter(r -> r.getFechaEmision().getYear() == ejercicio).toList();

        return cuentas.stream().map(c -> {
            BigDecimal pVal = mP.getOrDefault(c.getId(), BigDecimal.ZERO);
            BigDecimal[] saldos = mS.getOrDefault(c.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            List<String> terminos = ctasMap.getOrDefault(c.getId(), Collections.emptyList());

            BigDecimal cob = recsAnio.stream()
                    .filter(r -> r.getEstado() == Recibo.EstadoRecibo.COBRADO && terminos.stream().anyMatch(t -> r.getConcepto().toLowerCase().contains(t)))
                    .map(Recibo::getPagadoAcumulado).reduce(BigDecimal.ZERO, BigDecimal::add);

            return new DesviacionPresupuestoDTO(c.getId(), c.getCodigo(), c.getNombre(),
                    pVal, cob, saldos[1], saldos[1].subtract(cob), BigDecimal.ZERO);
        }).toList();
    }

    @Transactional(readOnly = true)
    public BalanceSituacion generarBalance(Long comunidadId) {
        int anio = LocalDate.now().getYear();
        LocalDate inicio = LocalDate.of(anio, 1, 1);
        LocalDate fin = LocalDate.of(anio, 12, 31);
        List<Object[]> resultados = movContableRepo.obtenerSaldosPorRango(comunidadId, inicio, fin);
        Map<Long, BigDecimal[]> mapaSaldos = new HashMap<>();
        for (Object[] fila : resultados) {
            mapaSaldos.put((Long) fila[0], new BigDecimal[]{(BigDecimal) fila[1], (BigDecimal) fila[2]});
        }
        List<CuentaContable> cuentas = cuentaContableRepository.findByComunidadId(comunidadId);
        BigDecimal activoBancos = BigDecimal.ZERO;
        BigDecimal activoVecinos = BigDecimal.ZERO;
        BigDecimal pasivoProv = BigDecimal.ZERO;
        BigDecimal ingresosTotales = BigDecimal.ZERO;
        BigDecimal gastosTotales = BigDecimal.ZERO;
        for (CuentaContable cta : cuentas) {
            BigDecimal[] s = mapaSaldos.getOrDefault(cta.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal d = s[0]; BigDecimal h = s[1]; String codigo = cta.getCodigo().trim();
            if (codigo.startsWith("572")) { activoBancos = activoBancos.add(d).subtract(h); }
            else if (codigo.startsWith("430")) { activoVecinos = activoVecinos.add(d).subtract(h); }
            else if (codigo.startsWith("41")) { pasivoProv = pasivoProv.add(h).subtract(d); }
            else if (codigo.startsWith("7")) { ingresosTotales = ingresosTotales.add(h).subtract(d); }
            else if (codigo.startsWith("6")) { gastosTotales = gastosTotales.add(d).subtract(h); }
        }
        BigDecimal resultado = ingresosTotales.subtract(gastosTotales);
        BigDecimal totalActivo = activoBancos.add(activoVecinos);
        BigDecimal totalPasivoPN = pasivoProv.add(resultado);
        return new BalanceSituacion(activoBancos, activoVecinos, BigDecimal.ZERO, pasivoProv, BigDecimal.ZERO, resultado, totalActivo, totalPasivoPN);
    }

    // =========================================================================
    // 2. GESTIÓN DE RECIBOS Y VECINOS (Generación, SEPA y Desglose)
    // =========================================================================

    @Transactional
    public void generarRecibosMes(Long comunidadId, int mes, int anio) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();
        List<Vecino> vecinos = vecinoRepository.findByComunidadId(comunidadId);

        for (Vecino v : vecinos) {
            for (ConceptoCobro cc : v.getListaConceptos()) {
                if (cc.correspondeMes(mes) && cc.getImporte().compareTo(BigDecimal.ZERO) > 0) {
                    String conceptoDesc = cc.getDescripcion();
                    if (!reciboRepo.existsByVecinoIdAndConceptoAndMesAndAnio(v.getId(), conceptoDesc, mes, anio)) {
                        Recibo r = new Recibo();
                        r.setComunidad(comunidad);
                        r.setVecino(v);
                        r.setFechaEmision(LocalDate.of(anio, mes, 1));
                        r.setImporte(cc.getImporte());
                        r.setPagadoAcumulado(BigDecimal.ZERO);
                        r.setEstado(Recibo.EstadoRecibo.PENDIENTE);
                        r.setConcepto(conceptoDesc);
                        reciboRepo.save(r);

                        ejecutarAsientoCobroInterno(r);
                    }
                }
            }
        }
    }

    @Transactional
    public void generarReciboDesdeSepa(Vecino vecino, BigDecimal importe, LocalDate fecha) {
        Recibo r = new Recibo();
        r.setVecino(vecino);
        r.setComunidad(vecino.getComunidad());
        r.setImporte(importe);
        r.setPagadoAcumulado(BigDecimal.ZERO);
        r.setFechaEmision(fecha);
        r.setEstado(Recibo.EstadoRecibo.PENDIENTE);

        String finca = (vecino.getVivienda() != null) ? vecino.getVivienda() : "";
        r.setConcepto("CUOTA COMUNIDAD " + finca);

        reciboRepo.save(r);
        ejecutarAsientoCobroInterno(r);
    }

    @Transactional
    public Recibo registrarDevengoCuota(Vecino v, BigDecimal imp, String con, LocalDate fecha, String tipo, String etiqueta) {
        Recibo r = new Recibo();
        r.setVecino(v);
        r.setComunidad(v.getComunidad());
        r.setImporte(imp);
        r.setPagadoAcumulado(BigDecimal.ZERO);
        r.setFechaEmision(fecha != null ? fecha : LocalDate.now());
        r.setEstado(Recibo.EstadoRecibo.PENDIENTE);
        r.setConcepto(con);

        // NUEVO: Guardamos el ADN del recibo para el borrado inteligente futuro
        r.setTipoRemesa(tipo != null ? tipo : "ORDINARIA");
        r.setEtiquetaExtra(etiqueta);

        Recibo guardado = reciboRepo.save(r);

        // Mantenemos tu funcionalidad original de ejecutar el asiento contable interno
        ejecutarAsientoCobroInterno(guardado);

        log.info("Devengo registrado para {} [{} - {}]: {} €", v.getNombre(), r.getTipoRemesa(), etiqueta, imp);
        return guardado;
    }

    @Transactional
    public void confirmarCobroRecibo(Long reciboId, Long movimientoBancarioId) {
        Recibo r = reciboRepo.findById(reciboId).orElseThrow();
        MovimientoBancario mov = movRepo.findById(movimientoBancarioId).orElseThrow();

        r.setEstado(Recibo.EstadoRecibo.COBRADO);
        r.setPagadoAcumulado(r.getImporte());
        r.setFechaCobroBanco(mov.getFechaOperacion());
        r.setMovimientoBancario(mov);
        reciboRepo.save(r);

        ejecutarAsientoCobroInterno(r);
    }

    @Transactional
    public void procesarCobroRemesaCompleta(Long comunidadId, List<Long> recibosIds, Long movimientoBancarioId) {
        MovimientoBancario mov = movRepo.findById(movimientoBancarioId)
                .orElseThrow(() -> new RuntimeException("Movimiento bancario no encontrado"));

        List<Recibo> recibos = reciboRepo.findAllById(recibosIds);
        String numAsiento = "REM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate fechaCobro = mov.getFechaOperacion();

        CuentaContable ctaBanco = cuentaContableRepository.findByComunidadIdAndTipo(comunidadId, TipoCuenta.ACTIVO)
                .stream().filter(c -> c.getCodigo().startsWith("572")).findFirst()
                .orElseThrow(() -> new RuntimeException("Cuenta 572 no encontrada"));

        registrarApunte(ctaBanco, mov.getImporte(), BigDecimal.ZERO, "Cobro Remesa: " + mov.getConcepto(), numAsiento, mov.getComunidad(), fechaCobro);

        for (Recibo r : recibos) {
            String codVecino = crearCuentaParaVecino(r.getVecino());
            CuentaContable ctaVecino = cuentaContableRepository.findByCodigoAndComunidadId(codVecino, comunidadId).get();

            registrarApunte(ctaVecino, BigDecimal.ZERO, r.getImporte(), "Cobro Recibo " + r.getConcepto(), numAsiento, r.getComunidad(), fechaCobro);

            r.setEstado(Recibo.EstadoRecibo.COBRADO);
            r.setPagadoAcumulado(r.getImporte());
            r.setFechaCobroBanco(fechaCobro);
            r.setMovimientoBancario(mov);
        }

        mov.setConciliado(true);
        reciboRepo.saveAll(recibos);
        movRepo.saveAndFlush(mov);
    }

    /**
     * PASO 2: LIMPIEZA INTEGRAL PARA REGENERACIÓN
     */
    @Transactional
    public void limpiarContabilidadMesAntesDeRemesa(Long comunidadId, int mes, int anio, String tipo, String etiqueta, boolean sustituir) {
        log.info("🧹 Iniciando limpieza selectiva periodo {}/{} para comunidad {} [Tipo: {}, Etiqueta: {}]",
                mes, anio, comunidadId, tipo, etiqueta);

        // Si es extraordinaria y el operador ha dicho que NO quiere sustituir,
        // salimos directamente sin borrar nada.
        if ("EXTRAORDINARIA".equals(tipo) && !sustituir) {
            log.info("GTI: Nueva extraordinaria detectada. Se mantienen recibos anteriores.");
            return;
        }

        // Buscamos los recibos de la comunidad que coincidan con el periodo y estén PENDIENTES
        List<Recibo> recibosABorrar = reciboRepo.findByComunidadId(comunidadId).stream()
                .filter(r -> r.getFechaEmision().getMonthValue() == mes
                        && r.getFechaEmision().getYear() == anio
                        && r.getEstado() == Recibo.EstadoRecibo.PENDIENTE)
                .filter(r -> {
                    // FILTRADO INTELIGENTE:
                    if ("ORDINARIA".equals(tipo)) {
                        // Si estamos generando la Ordinaria, solo borramos las Ordinarias previas
                        return "ORDINARIA".equals(r.getTipoRemesa());
                    } else {
                        // Si estamos generando una Extraordinaria y sustituir es true,
                        // solo borramos los recibos que tengan EXACTAMENTE la misma etiqueta.
                        return "EXTRAORDINARIA".equals(r.getTipoRemesa())
                                && etiqueta != null && etiqueta.equals(r.getEtiquetaExtra());
                    }
                })
                .toList();

        // Procedemos al borrado de los recibos filtrados y sus movimientos contables
        for (Recibo r : recibosABorrar) {
            // Mantenemos tu funcionalidad original de eliminar el asiento contable REC-ID
            movContableRepo.deleteAll(movContableRepo.findByNumeroAsiento("REC-" + r.getId()));
            reciboRepo.delete(r);
        }

        log.info("GTI: Limpieza finalizada. Eliminados {} recibos y sus asientos correspondientes.", recibosABorrar.size());
    }

    // =========================================================================
    // 3. LOGICA DE GASTOS, CONCILIACIÓN Y APERTURA
    // =========================================================================

    @Transactional
    public void registrarGastoContable(Gasto gasto) {
        ejecutarRegistroGastoInterno(gasto);
    }

    @Transactional
    public void repartirGasto(Long comunidadId, BigDecimal importeTotal, String descripcionGasto) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();
        List<Vecino> vecinosActivos = comunidad.getVecinos().stream()
                .filter(Vecino::isActivo)
                .toList();

        if (vecinosActivos.isEmpty()) return;

        for (Vecino v : vecinosActivos) {
            BigDecimal cuotaVecino;
            if (comunidad.getTipoReparto() == TipoReparto.COEFICIENTE) {
                BigDecimal factor = v.getCoeficiente().divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
                cuotaVecino = importeTotal.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            } else {
                cuotaVecino = importeTotal.divide(new BigDecimal(vecinosActivos.size()), 2, RoundingMode.HALF_UP);
            }
            log.info("Reparto para {}: {} EUR", v.getNombre(), cuotaVecino);
        }
    }

    @Transactional
    public void confirmarPagoGasto(Long gastoId, LocalDate fechaPago) {
        Gasto gasto = gastoRepository.findById(gastoId).orElseThrow();
        String numAsiento = "PAG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        CuentaContable ctaProv = obtenerOCrearCuentaProveedor(gasto.getProveedor(), gasto.getComunidad());
        CuentaContable ctaBanco = cuentaContableRepository.findByComunidadIdAndTipo(gasto.getComunidad().getId(), TipoCuenta.ACTIVO)
                .stream().filter(c -> c.getCodigo().startsWith("572")).findFirst()
                .orElseThrow(() -> new RuntimeException("Cuenta 572 no encontrada"));

        registrarApunte(ctaProv, gasto.getImporteTotal(), BigDecimal.ZERO, "Pago Fra: " + gasto.getNumeroFactura(), numAsiento, gasto.getComunidad(), fechaPago);
        registrarApunte(ctaBanco, BigDecimal.ZERO, gasto.getImporteTotal(), "Salida Banco", numAsiento, gasto.getComunidad(), fechaPago);

        gasto.setPagado(true);
        gasto.setFechaPago(fechaPago);
        gastoRepository.save(gasto);
    }

    @Transactional
    public void conciliarManualDirecto(Long movBancarioId, Long cuentaId) {
        MovimientoBancario mov = movRepo.findById(movBancarioId).orElseThrow();
        CuentaContable cta = cuentaContableRepository.findById(cuentaId).orElseThrow();
        String numAsiento = "CONC-DIR-MOV" + movBancarioId + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        BigDecimal imp = mov.getImporte().abs();
        BigDecimal debe = "1".equals(mov.getSigno()) ? imp : BigDecimal.ZERO;
        BigDecimal haber = "2".equals(mov.getSigno()) ? imp : BigDecimal.ZERO;

        registrarApunte(cta, debe, haber, "Conciliación: " + mov.getConcepto(), numAsiento, mov.getComunidad(), mov.getFechaOperacion());

        CuentaContable ctaBanco = cuentaContableRepository.findByComunidadIdAndTipo(mov.getComunidad().getId(), TipoCuenta.ACTIVO)
                .stream().filter(c -> c.getCodigo().startsWith("572")).findFirst().orElseThrow();

        registrarApunte(ctaBanco, haber, debe, "Banco: " + mov.getConcepto(), numAsiento, mov.getComunidad(), mov.getFechaOperacion());

        mov.setConciliado(true);
        movRepo.save(mov);
    }

    @Transactional
    public void registrarSaldoInicial(Long cuentaId, BigDecimal importe, LocalDate fecha) {
        CuentaContable cuenta = cuentaContableRepository.findById(cuentaId).orElseThrow();
        Comunidad comunidad = cuenta.getComunidad();
        CuentaContable ctaContra = cuentaContableRepository.findByCodigoAndComunidadId("12900000", comunidad.getId())
                .orElseGet(() -> cuentaContableRepository.save(new CuentaContable("12900000", "Resultados Ejercicios Anteriores", TipoCuenta.PASIVO, comunidad)));

        String numAsiento = "APE-" + fecha.getYear();
        if (cuenta.getTipo() == TipoCuenta.ACTIVO || cuenta.getTipo() == TipoCuenta.VECINO) {
            registrarApunte(cuenta, importe, BigDecimal.ZERO, "Saldo Inicial", numAsiento, comunidad, fecha);
            registrarApunte(ctaContra, BigDecimal.ZERO, importe, "Apertura Patrimonio", numAsiento, comunidad, fecha);
        } else {
            registrarApunte(cuenta, BigDecimal.ZERO, importe, "Saldo Inicial", numAsiento, comunidad, fecha);
            registrarApunte(ctaContra, importe, BigDecimal.ZERO, "Apertura Patrimonio", numAsiento, comunidad, fecha);
        }
    }

    @Transactional
    public void regenerarContabilidadCompleta(Long comunidadId) {
        movContableRepo.deleteByComunidadIdExceptApertura(comunidadId);
        gastoRepository.findByComunidadIdOrderByFechaDesc(comunidadId).forEach(this::ejecutarRegistroGastoInterno);
        reciboRepo.findByComunidadId(comunidadId).forEach(this::ejecutarAsientoCobroInterno);
    }

    @Transactional
    public void desconciliarMovimientoBancario(Long movId) {
        MovimientoBancario mov = movRepo.findById(movId).orElseThrow();
        movContableRepo.deleteAll(movContableRepo.findByNumeroAsientoLike("CONC-DIR-MOV" + movId + "-%"));

        List<Recibo> recibosLigados = reciboRepo.findByMovimientoBancarioId(movId);
        for (Recibo r : recibosLigados) {
            r.setEstado(Recibo.EstadoRecibo.PENDIENTE);
            r.setFechaCobroBanco(null);
            r.setMovimientoBancario(null);
            r.setPagadoAcumulado(BigDecimal.ZERO);
            reciboRepo.save(r);
            movContableRepo.deleteAll(movContableRepo.findByNumeroAsiento("COB-" + r.getId()));
        }
        mov.setConciliado(false);
        movRepo.saveAndFlush(mov);
    }

    // =========================================================================
    // 4. MÉTODOS DE SOPORTE (Helpers Privados)
    // =========================================================================

    /**
            * REGISTRO INTEGRAL GTI: Procesa el asiento de devengo (6 -> 4).
            * Este método garantiza que no existan duplicados borrando cualquier rastro previo
     * antes de realizar los nuevos apuntes.
            */
    private void ejecutarRegistroGastoInterno(Gasto gasto) {
        log.info("GTI SYNC: Iniciando proceso contable para Gasto ID: {}", gasto.getId());

        // 1. LIMPIEZA PREVIA ATÓMICA
        // Si el gasto ya tiene un número de asiento (aunque sea técnico como 'FRA-XXX'),
        // borramos todos los movimientos del diario que usen ese identificador.
        if (gasto.getNumeroAsiento() != null && !gasto.getNumeroAsiento().isEmpty()) {
            log.info("GTI: Limpiando rastro del asiento previo: {}", gasto.getNumeroAsiento());
            movContableRepo.deleteByNumeroAsiento(gasto.getNumeroAsiento());

            // CRÍTICO: El flush obliga a la base de datos a ejecutar el DELETE ahora mismo.
            // Sin esto, Hibernate podría intentar hacer los INSERT nuevos antes que el DELETE,
            // causando los duplicados que ves en el diario.
            movContableRepo.flush();
        } else {
            // Si es un gasto nuevo, generamos un identificador técnico único.
            // Nota: La "Renumeración Legal" lo convertirá después en un número sencillo (1, 2, 3...).
            gasto.setNumeroAsiento("GAS-" + System.currentTimeMillis() + "-" + gasto.getId());
        }

        // 2. CONSTRUCCIÓN DEL CONCEPTO
        String concepto = "Fra. " + (gasto.getNumeroFactura() != null ? gasto.getNumeroFactura() : "S/N")
                + " - " + gasto.getProveedor();

        // 3. APUNTE AL DEBE: Cuenta de Gasto (Grupo 6)
        // Refleja el consumo/gasto de la comunidad.
        registrarApunte(
                gasto.getCuentaGasto(),
                gasto.getImporteTotal(),
                BigDecimal.ZERO,
                concepto,
                gasto.getNumeroAsiento(),
                gasto.getComunidad(),
                gasto.getFecha()
        );

        // 4. APUNTE AL HABER: Cuenta de Acreedor/Proveedor (Grupo 410)
        // Refleja la deuda contraída con el tercero.
        CuentaContable ctaProv = obtenerOCrearCuentaProveedor(gasto.getProveedor(), gasto.getComunidad());

        registrarApunte(
                ctaProv,
                BigDecimal.ZERO,
                gasto.getImporteTotal(),
                "PROVEEDOR: " + gasto.getProveedor(),
                gasto.getNumeroAsiento(),
                gasto.getComunidad(),
                gasto.getFecha()
        );

        // 5. ACTUALIZACIÓN DE LA ENTIDAD GASTO
        // Guardamos el gasto para asegurar que el NumeroAsiento quede grabado.
        gastoRepository.save(gasto);

        log.info("✅ Asiento de devengo registrado/actualizado: {} para {}",
                gasto.getNumeroAsiento(), gasto.getProveedor());
    }

    private void ejecutarAsientoCobroInterno(Recibo r) {

        movContableRepo.deleteByNumeroAsiento("REC-" + r.getId());
        movContableRepo.deleteByNumeroAsiento("COB-" + r.getId());
        movContableRepo.flush();

        CuentaContable ctaIngreso = buscarCuentaPorConceptoRecibo(r);

        String codVecino = crearCuentaParaVecino(r.getVecino());

        CuentaContable ctaVecino =
                cuentaContableRepository
                        .findByCodigoAndComunidadId(
                                codVecino,
                                r.getComunidad().getId())
                        .orElseThrow();

        String numAsientoEmision = "REC-" + r.getId();

        BigDecimal base = r.getImporte();
        BigDecimal impuesto = BigDecimal.ZERO;
        BigDecimal total = base;

        TipoImpuesto tipoImpuesto = TipoImpuesto.EXENTO;
        BigDecimal porcentaje = BigDecimal.ZERO;

        Optional<ConceptoCobro> conceptoOpt =
                conceptoCobroRepo.findAllGenericConcepts()
                        .stream()
                        .filter(c ->
                                r.getConcepto()
                                        .toLowerCase()
                                        .contains(c.getDescripcion().toLowerCase()))
                        .findFirst();

        if (conceptoOpt.isPresent()) {

            ConceptoCobro cc = conceptoOpt.get();

            if (cc.getTipoImpuesto() != null) {
                tipoImpuesto = cc.getTipoImpuesto();
            }

            if (cc.getPorcentajeImpuesto() != null) {
                porcentaje = cc.getPorcentajeImpuesto();
            }
        }

        if (tipoImpuesto != TipoImpuesto.EXENTO
                && porcentaje.compareTo(BigDecimal.ZERO) > 0) {

            impuesto = base
                    .multiply(
                            porcentaje.divide(
                                    new BigDecimal("100"),
                                    4,
                                    RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.HALF_UP);

            total = base.add(impuesto);
        }

        registrarApunte(
                ctaVecino,
                total,
                BigDecimal.ZERO,
                "Emisión " + r.getConcepto(),
                numAsientoEmision,
                r.getComunidad(),
                r.getFechaEmision()
        );

        registrarApunte(
                ctaIngreso,
                BigDecimal.ZERO,
                base,
                "Ingreso " + r.getConcepto(),
                numAsientoEmision,
                r.getComunidad(),
                r.getFechaEmision()
        );

        if (impuesto.compareTo(BigDecimal.ZERO) > 0) {

            CuentaContable cuentaImpuesto = obtenerCuentaImpuesto(
                    tipoImpuesto,
                    r.getComunidad()
            );

            registrarApunte(
                    cuentaImpuesto,
                    BigDecimal.ZERO,
                    impuesto,
                    tipoImpuesto.name() + " repercutido",
                    numAsientoEmision,
                    r.getComunidad(),
                    r.getFechaEmision()
            );
        }

        if (r.getEstado() == Recibo.EstadoRecibo.COBRADO) {

            String numAsientoCobro = "COB-" + r.getId();

            CuentaContable ctaBanco =
                    cuentaContableRepository
                            .findByComunidadIdAndTipo(
                                    r.getComunidad().getId(),
                                    TipoCuenta.ACTIVO)
                            .stream()
                            .filter(c -> c.getCodigo().startsWith("572"))
                            .findFirst()
                            .orElseThrow();

            LocalDate fechaRealCobro =
                    (r.getFechaCobroBanco() != null)
                            ? r.getFechaCobroBanco()
                            : LocalDate.now();

            registrarApunte(
                    ctaBanco,
                    total,
                    BigDecimal.ZERO,
                    "Cobro Banco " + r.getConcepto(),
                    numAsientoCobro,
                    r.getComunidad(),
                    fechaRealCobro
            );

            registrarApunte(
                    ctaVecino,
                    BigDecimal.ZERO,
                    total,
                    "Cancelación deuda " + r.getVecino().getNombre(),
                    numAsientoCobro,
                    r.getComunidad(),
                    fechaRealCobro
            );
        }
    }

    private CuentaContable buscarCuentaPorConceptoRecibo(Recibo r) {
        // 1. Buscamos si el concepto tiene una cuenta asociada
        return conceptoCobroRepo.findAllGenericConcepts().stream()
                .filter(gc -> r.getConcepto().toLowerCase().contains(gc.getDescripcion().toLowerCase()))
                .map(gc -> {
                    // CORRECCIÓN CRÍTICA: No usamos el ID de la cuenta del concepto genérico,
                    // porque puede pertenecer a otra comunidad. Buscamos el MISMO CÓDIGO en la comunidad actual.
                    if (gc.getCuentaContable() != null) {
                        String codigoCuenta = gc.getCuentaContable().getCodigo();
                        return cuentaContableRepository.findByCodigoAndComunidadId(codigoCuenta, r.getComunidad().getId()).orElse(null);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                // 2. Si no hay mapeo, usamos la 73100001 por defecto de ESTA comunidad
                .orElseGet(() -> cuentaContableRepository.findByCodigoAndComunidadId("73100001", r.getComunidad().getId())
                        .orElseThrow(() -> new RuntimeException("Error: No existe cuenta de ingresos 73100001 for this community")));
    }

    private BigDecimal obtenerSaldoCobradoPorMapeoGenerico(Long comunidadId, CuentaContable cuenta, int anio) {
        List<String> descBase = conceptoCobroRepo.findAllGenericConcepts().stream()
                .filter(gc -> gc.getCuentaContable() != null && gc.getCuentaContable().getId().equals(cuenta.getId()))
                .map(ConceptoCobro::getDescripcion).toList();
        if (descBase.isEmpty()) return BigDecimal.ZERO;
        return reciboRepo.findByComunidadId(comunidadId).stream()
                .filter(r -> r.getFechaEmision().getYear() == anio && r.getEstado() == Recibo.EstadoRecibo.COBRADO)
                .filter(r -> descBase.stream().anyMatch(db -> r.getConcepto().toLowerCase().contains(db.toLowerCase())))
                .map(Recibo::getPagadoAcumulado).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // =========================================================================
    // 5. UTILIDADES, CÁLCULOS Y AUTOMATIZACIÓN
    // =========================================================================

    private void actualizarConceptoCuotaVecino(Vecino v, BigDecimal importe) {
        ConceptoCobro cuota = v.getListaConceptos().stream()
                .filter(cc -> cc.getDescripcion().toLowerCase().contains("cuota"))
                .findFirst()
                .orElseGet(() -> {
                    ConceptoCobro nuevo = new ConceptoCobro();
                    nuevo.setVecino(v);
                    nuevo.setComunidad(v.getComunidad());
                    nuevo.setPeriodicidad(ConceptoCobro.Periodicidad.MENSUAL);
                    nuevo.setActivo(true);
                    return nuevo;
                });

        String vivienda = (v.getVivienda() != null) ? v.getVivienda() : "";
        cuota.setDescripcion("CUOTA COMUNIDAD " + vivienda);
        cuota.setMesInicio(LocalDate.now().getMonthValue());
        cuota.setImporte(importe);
        conceptoCobroRepo.save(cuota);
    }

    @Transactional
    public void calcularCuotasDesdePresupuesto(Long comunidadId, int anio) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();
        BigDecimal totalPresupuestoAnual = presupuestoRepo.findByComunidadIdAndAnio(comunidadId, anio)
                .stream().map(Presupuesto::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add);

        for (Vecino v : comunidad.getVecinos()) {
            if (!v.isActivo()) continue;
            BigDecimal factor = v.getCoeficiente().divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            BigDecimal cuotaMensual = totalPresupuestoAnual.multiply(factor).divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
            actualizarConceptoCuotaVecino(v, cuotaMensual);
        }
    }

    @Transactional
    public void inicializarPlanContable(Comunidad comunidad) {

        log.info("Inicializando Plan Contable Maestro para comunidad: {}", comunidad.getNombre());

        String[][] planExtra = {

                // GASTOS
                {"62800001", "Suministro Eléctrico (Escalera/Portal)", "GASTO"},
                {"62800003", "Suministro de Agua", "GASTO"},
                {"62900001", "Servicio de Limpieza", "GASTO"},

                // BANCOS
                {"57200001", "Banco Principal c/c", "ACTIVO"},

                // INGRESOS COMUNIDAD
                {"73100001", "Cuotas ordinarias generales", "INGRESO"},
                {"73100002", "Derramas extraordinarias", "INGRESO"},
                {"73100003", "Ingresos varios", "INGRESO"},

                // HONORARIOS ADMINISTRACIÓN
                {"70000001", "Honorarios Administración", "INGRESO"},

                // IMPUESTOS REPERCUTIDOS
                {"47700001", "HP IVA Repercutido", "PASIVO"},
                {"47700002", "HP IPSI Repercutido", "PASIVO"},
                {"47770001", "HP IGIC Repercutido", "PASIVO"}
        };

        for (String[] c : planExtra) {

            if (!cuentaContableRepository.existsByCodigoAndComunidadId(
                    c[0],
                    comunidad.getId()
            )) {

                cuentaContableRepository.save(
                        new CuentaContable(
                                c[0],
                                c[1],
                                TipoCuenta.valueOf(c[2]),
                                comunidad
                        )
                );
            }
        }
    }

    // =========================================================================
    // 6. UTILIDADES Y MAYOR (MANTENIDO ÍNTEGRO)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<BalanceComprobacionDTO> generarBalanceComprobacion(Long comunidadId, int anio) {
        List<CuentaContable> cuentas = cuentaContableRepository.findByComunidadId(comunidadId);

        // 1. CARGA MASIVA: Traemos todos los saldos del año en un solo viaje (RAM Mode)
        Map<Long, BigDecimal[]> mapaSaldos = new HashMap<>();
        movContableRepo.obtenerTodosLosSaldosAnuales(comunidadId, anio)
                .forEach(s -> mapaSaldos.put((Long)s[0], new BigDecimal[]{(BigDecimal)s[1], (BigDecimal)s[2]}));

        List<BalanceComprobacionDTO> balance = new ArrayList<>();
        for (CuentaContable cta : cuentas) {
            // 2. PROCESAMIENTO 100% RAM: Sin consultas a la DB dentro del bucle
            BigDecimal[] s = mapaSaldos.getOrDefault(cta.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal d = s[0]; // Suma DEBE
            BigDecimal h = s[1]; // Suma HABER
            BigDecimal sd = d.subtract(h);

            balance.add(new BalanceComprobacionDTO(cta.getCodigo(), cta.getNombre(), d, h,
                    sd.compareTo(BigDecimal.ZERO) > 0 ? sd : BigDecimal.ZERO,
                    sd.compareTo(BigDecimal.ZERO) < 0 ? sd.abs() : BigDecimal.ZERO));
        }
        return balance;
    }

    @Transactional(readOnly = true)
    public List<MovimientoMayorDTO> obtenerLibroMayorConSaldo(Long cuentaId, int anio) {
        List<MovimientoContable> movs = movContableRepo.findByCuentaIdAndAnioOrderByFechaAsc(cuentaId, anio);
        List<MovimientoMayorDTO> mayor = new ArrayList<>();
        BigDecimal saldo = BigDecimal.ZERO;
        for (MovimientoContable m : movs) {
            saldo = saldo.add(m.getDebe()).subtract(m.getHaber());
            mayor.add(new MovimientoMayorDTO(m.getFecha(), m.getConcepto(), m.getNumeroAsiento(), m.getDebe(), m.getHaber(), saldo));
        }
        return mayor;
    }

    /**
     * RENUMERACIÓN LEGAL GTI: Ordena cronológicamente todos los asientos del año
     * y les asigna un número secuencial (1, 2, 3...) sin saltos.
     */
    @Transactional
    public void renumerarAsientosEjercicio(Long comunidadId, int ejercicio) {
        // 1. Obtenemos todos los movimientos del año ordenados por fecha e ID técnico
        List<MovimientoContable> movimientos = movContableRepo.findByComunidadIdAndAnio(
                        comunidadId, ejercicio, org.springframework.data.domain.Pageable.unpaged()).getContent()
                .stream()
                .sorted(Comparator.comparing(MovimientoContable::getFecha)
                        .thenComparing(MovimientoContable::getId))
                .collect(Collectors.toList());

        if (movimientos.isEmpty()) return;

        Map<String, String> mapaRenumeracion = new HashMap<>();
        int contadorAsiento = 1;

        for (MovimientoContable mov : movimientos) {
            String antiguoNumero = mov.getNumeroAsiento();

            // Si el asiento ya ha sido procesado (varios apuntes con mismo número), usamos el nuevo asignado
            if (!mapaRenumeracion.containsKey(antiguoNumero)) {
                mapaRenumeracion.put(antiguoNumero, String.valueOf(contadorAsiento));
                contadorAsiento++;
            }

            // Actualizamos el número de asiento en el apunte
            mov.setNumeroAsiento(mapaRenumeracion.get(antiguoNumero));
            movContableRepo.save(mov);
        }

        log.info("✅ Renumeración finalizada: {} asientos procesados para comunidad {}", (contadorAsiento - 1), comunidadId);
    }

    @Transactional
    public void sincronizarContabilidadExistente(Long id) {
        log.info("GTI SYNC: Verificando e inicializando plan contable estructural para comunidad ID: {}", id);
        comunidadRepository.findById(id).ifPresent(this::inicializarPlanContable);
    }

    @Transactional
    public void deshacerConciliacionRecibo(Long reciboId) {
        Recibo r = reciboRepo.findById(reciboId)
                .orElseThrow(() -> new RuntimeException("Recibo no encontrado ID: " + reciboId));

        log.info("🧹 Anulando cobro de recibo {} y limpiando Libro Diario...", reciboId);

        // BORRADO FÍSICO DE LOS ASIENTOS DE COBRO EN EL DIARIO
        movContableRepo.deleteByNumeroAsiento("COB-" + reciboId);

        // CORRECCIÓN LÍNEA 600: Usamos 'movRepo' que es el nombre definido en el constructor
        if (r.getMovimientoBancario() != null) {
            MovimientoBancario mb = r.getMovimientoBancario();
            mb.setConciliado(false);
            movRepo.save(mb); // <--- REPARADO AQUÍ
        }

        r.setEstado(Recibo.EstadoRecibo.PENDIENTE);
        r.setFechaCobroBanco(null);
        r.setMovimientoBancario(null);
        r.setPagadoAcumulado(BigDecimal.ZERO);
        reciboRepo.save(r);

        log.info("✅ Recibo {} restaurado a PENDIENTE.", reciboId);
    }

    @Transactional(readOnly = true) public List<DesviacionPresupuestoDTO> obtenerInformeDesviaciones(Long id, int anio) { return obtenerInformeGastosReal(id, anio); }

    public String crearCuentaParaVecino(Vecino vecino) {
        String cuenta = "430" + String.format("%05d", vecino.getId() != null ? vecino.getId() : 0);
        if (cuentaContableRepository.findByCodigoAndComunidadId(cuenta, vecino.getComunidad().getId()).isEmpty()) {
            cuentaContableRepository.save(new CuentaContable(cuenta, "PROPIETARIO: " + vecino.getNombre().toUpperCase(), TipoCuenta.VECINO, vecino.getComunidad()));
        }
        return cuenta;
    }

    /**
     * Registra un apunte individual en el Libro Diario.
     * MEJORA GTI: Filtra apuntes con importe cero para mantener la limpieza.
     */
    public void registrarApunte(CuentaContable cta, BigDecimal debe, BigDecimal haber,
                                String concepto, String asiento, Comunidad com, LocalDate fecha) {

        if (cta == null || com == null) return;

        // SEGURIDAD: Si ambos importes son 0 o nulos, no creamos el apunte
        BigDecimal dVal = (debe != null) ? debe : BigDecimal.ZERO;
        BigDecimal hVal = (haber != null) ? haber : BigDecimal.ZERO;

        if (dVal.compareTo(BigDecimal.ZERO) == 0 && hVal.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("GTI: Omitiendo apunte con importe 0 para la cuenta {}", cta.getCodigo());
            return;
        }

        MovimientoContable m = new MovimientoContable();
        m.setCuenta(cta);
        m.setDebe(dVal);
        m.setHaber(hVal);
        m.setConcepto(concepto);
        m.setNumeroAsiento(asiento);
        m.setFecha(fecha != null ? fecha : LocalDate.now());
        m.setComunidad(com);

        // Guardamos el apunte
        movContableRepo.save(m);
    }

    private BigDecimal obtenerSaldoDebe(Long cuentaId, int anio) {
        BigDecimal total = movContableRepo.sumDebeByCuentaAndAno(cuentaId, anio);
        return (total != null) ? total : BigDecimal.ZERO;
    }

    private BigDecimal obtenerSaldoHaber(Long cuentaId, int anio) {
        BigDecimal total = movContableRepo.sumHaberByCuentaAndAno(cuentaId, anio);
        return (total != null) ? total : BigDecimal.ZERO;
    }

    private BigDecimal obtenerSaldoPagadoReal(Long cuentaId, int anio) {
        BigDecimal total = gastoRepository.sumImportePagadoByCuentaAndAnio(cuentaId, anio);
        return (total != null) ? total : BigDecimal.ZERO;
    }

    private CuentaContable obtenerOCrearCuentaProveedor(String nombre, Comunidad com) {
        String codigo = "410" + String.format("%05d", Math.abs(nombre.hashCode() % 100000));
        return cuentaContableRepository.findByCodigoAndComunidadId(codigo, com.getId())
                .orElseGet(() -> cuentaContableRepository.save(new CuentaContable(codigo, "PROV: " + nombre, TipoCuenta.PASIVO, com)));
    }

    private CuentaContable obtenerCuentaImpuesto(
            TipoImpuesto tipo,
            Comunidad comunidad) {

        String codigo;

        switch (tipo) {
            case IVA:
                codigo = "47700001";
                break;

            case IPSI:
                codigo = "47700002";
                break;

            case IGIC:
                codigo = "47770001";
                break;

            default:
                log.warn("GTI: Tipo de impuesto {} no configurado para contabilidad.", tipo);
                return null;
        }

        // Forzamos java.lang.RuntimeException para evitar el error de "6 argumentos"
        return cuentaContableRepository
                .findByCodigoAndComunidadId(
                        codigo,
                        comunidad.getId()
                )
                .orElseThrow(() ->
                        new java.lang.RuntimeException("No existe cuenta contable impuesto: " + codigo)
                );
    }

    @Transactional
    public void borrarRecibosYcontabilidadDelMes(
            Long comunidadId,
            int mes,
            int anio,
            String tipo,
            String etiqueta,
            boolean sustituir) {

        log.info("🧹 GTI: Iniciando borrado selectivo {}/{} - Tipo: {} - Etiqueta: {}", mes, anio, tipo, etiqueta);

        // Si es extraordinaria y el operador elige "Añadir" (sustituir=false),
        // no borramos nada y salimos.
        if ("EXTRAORDINARIA".equals(tipo) && !sustituir) {
            log.info("GTI: No se borra nada, se procede a añadir nueva remesa.");
            return;
        }

        // Buscamos los recibos que coincidan con el filtro
        List<Recibo> pendientes = reciboRepo.findByComunidadId(comunidadId).stream()
                .filter(r -> r.getFechaEmision().getMonthValue() == mes
                        && r.getFechaEmision().getYear() == anio
                        && r.getEstado() == Recibo.EstadoRecibo.PENDIENTE)
                .filter(r -> {
                    // Lógica de distinción:
                    if ("ORDINARIA".equals(tipo)) {
                        return "ORDINARIA".equals(r.getTipoRemesa());
                    } else {
                        return "EXTRAORDINARIA".equals(r.getTipoRemesa())
                                && etiqueta != null && etiqueta.equals(r.getEtiquetaExtra());
                    }
                })
                .toList();

        for (Recibo r : pendientes) {
            try {
                // Borramos los asientos contables asociados
                movContableRepo.deleteAll(movContableRepo.findByNumeroAsiento("REC-" + r.getId()));

                // Borramos el recibo
                reciboRepo.delete(r);
            } catch (Exception e) {
                // Aquí es donde te daba el error de los 6 argumentos. Corregido con java.lang.
                throw new java.lang.RuntimeException("Error al borrar contabilidad del recibo " + r.getId());
            }
        }

        log.info("GTI: Borrado finalizado con éxito.");
    }

    @Transactional
    public void confirmarCobroReciboManual(Long reciboId, LocalDate fechaCobro) {
        Recibo r = reciboRepo.findById(reciboId)
                .orElseThrow(() -> new RuntimeException("Recibo no encontrado"));

        r.setEstado(Recibo.EstadoRecibo.COBRADO);
        r.setPagadoAcumulado(r.getImporte());
        r.setFechaCobroBanco(fechaCobro);
        reciboRepo.save(r);

        ejecutarAsientoCobroInterno(r);

        log.info("✅ Cobro MANUAL registrado para {}: {} € el día {}",
                r.getVecino().getNombre(), r.getImporte(), fechaCobro);
    }

    @Transactional
    public void borrarGastoConAsiento(Long gastoId) {
        Gasto gasto = gastoRepository.findById(gastoId)
                .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));
        if (gasto.getNumeroAsiento() != null) {
            movContableRepo.deleteByNumeroAsiento(gasto.getNumeroAsiento());
        }
        gastoRepository.delete(gasto);
    }

    @Transactional
    public void borrarAsientoCompleto(String numeroAsiento) {
        log.info("Eliminando asiento completo del diario: {}", numeroAsiento);
        movContableRepo.deleteByNumeroAsiento(numeroAsiento);
    }

    @Transactional
    public void desconciliarTodoElExtracto(Long comunidadId) {
        log.info("⚠️ Iniciando desconciliación masiva para la comunidad {}", comunidadId);
        List<MovimientoBancario> movimientos = movRepo.findByComunidadIdOrderByFechaOperacionAsc(comunidadId);
        for (MovimientoBancario mov : movimientos) {
            if (mov.isConciliado()) {
                desconciliarMovimientoBancario(mov.getId());
            }
        }
        log.info("✅ Desconciliación completada.");
    }

    @Transactional
    public void vaciarExtractoBancario(Long comunidadId) {
        log.warn("☢️ INICIANDO LIMPIEZA NUCLEAR PARA COMUNIDAD: {}", comunidadId);

        // 1. Resetear recibos Y DESVINCULAR BANCO (Crucial para evitar el error 1451)
        List<Recibo> recibos = reciboRepo.findAll();
        recibos.stream()
                .filter(r -> r.getVecino().getComunidad().getId().equals(comunidadId))
                .forEach(r -> {
                    r.setEstado(Recibo.EstadoRecibo.PENDIENTE);
                    r.setFechaCobroBanco(null);
                    r.setPagadoAcumulado(BigDecimal.ZERO);
                    r.setMovimientoBancario(null); // <--- ESTO EVITA EL ERROR DE CLAVE FORÁNEA
                    reciboRepo.save(r);
                });

        // 2. Limpiar la marca de los Gastos
        List<Gasto> gastos = gastoRepository.findByComunidadId(comunidadId);
        for (Gasto g : gastos) {
            g.setNumeroAsiento(null);
            gastoRepository.save(g);
        }

        // 3. Borrar Movimientos Contables (Diario/Mayor)
        movContableRepo.deleteByComunidadId(comunidadId);

        // 4. Borrar Movimientos Bancarios (Ahora sí dejará borrarlos)
        List<MovimientoBancario> movimientosBanco = movRepo.findByComunidadIdOrderByFechaOperacionAsc(comunidadId);
        movRepo.deleteAll(movimientosBanco);

        log.info("✅ Limpieza nuclear REAL completada para comunidad {}", comunidadId);
    }

    /**
     * Crea un asiento de apertura manual en el Libro Diario.
     * Útil para registrar saldos iniciales (como los 11.039,19€ de Malgrat).
     */
    @Transactional
    public void crearAsientoAperturaManual(Long comunidadId, LocalDate fecha, Long cuentaId, BigDecimal importe) {
        // 1. Localizamos la comunidad y la cuenta (ej: la 57200001)
        Comunidad com = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        CuentaContable cuenta = cuentaContableRepository.findById(cuentaId)
                .orElseThrow(() -> new RuntimeException("Cuenta contable no encontrada"));

        // 2. Creamos el apunte contable
        MovimientoContable asiento = new MovimientoContable();
        asiento.setComunidad(com);
        asiento.setFecha(fecha);
        asiento.setCuenta(cuenta);
        asiento.setConcepto("ASIENTO DE APERTURA: SALDO INICIAL");

        // Generamos un identificador único para el asiento
        asiento.setNumeroAsiento("APE-" + System.currentTimeMillis());

        // El saldo inicial en una cuenta de banco (Activo) aumenta por el DEBE
        asiento.setDebe(importe);
        asiento.setHaber(BigDecimal.ZERO);

        // 3. Guardamos en el Libro Diario
        movContableRepo.save(asiento);

        log.info("✅ Asiento de apertura registrado: {} € en la cuenta {} para {}",
                importe, cuenta.getCodigo(), com.getNombre());
    }

    @Transactional
    public void regenerarAsientosGastos(Long comunidadId) {
        log.info("GTI TURBO: Iniciando regeneración de asientos de DEVENGO para comunidad {}", comunidadId);

        List<Gasto> gastos = gastoRepository.findByComunidadId(comunidadId);

        for (Gasto gasto : gastos) {
            // MODO FORZADO: Borramos cualquier rastro previo para evitar bloqueos
            gasto.setNumeroAsiento(null);

            String uidAsiento = "GAS-DEV-" + System.currentTimeMillis() + "-" + gasto.getId();

            // 1. APUNTE AL DEBE: Cuenta de Gasto (Grupo 6)
            MovimientoContable debe = new MovimientoContable();
            debe.setComunidad(gasto.getComunidad());
            debe.setFecha(gasto.getFecha()); // Si esto es null, fallará (mira la solución abajo)
            debe.setCuenta(gasto.getCuentaGasto());
            debe.setConcepto("FACTURA " + gasto.getNumeroFactura() + " - " + gasto.getProveedor());
            debe.setDebe(gasto.getImporteTotal());
            debe.setHaber(BigDecimal.ZERO);
            debe.setNumeroAsiento(uidAsiento);
            movContableRepo.save(debe);

            // 2. APUNTE AL HABER: Cuenta del Acreedor/Proveedor (Grupo 410)
            CuentaContable cuentaAcreedor = cuentaContableRepository.findByCodigoAndComunidadId("41000000", comunidadId)
                    .orElseGet(() -> {
                        log.warn("41000000 genérica no encontrada, buscando por nombre...");
                        return cuentaContableRepository.findByComunidadId(comunidadId).stream()
                                .filter(c -> c.getCodigo().startsWith("410") && c.getNombre().contains(gasto.getProveedor()))
                                .findFirst()
                                .orElse(gasto.getCuentaGasto());
                    });

            MovimientoContable haber = new MovimientoContable();
            haber.setComunidad(gasto.getComunidad());
            haber.setFecha(gasto.getFecha());
            haber.setCuenta(cuentaAcreedor);
            haber.setConcepto("PROVEEDOR: " + gasto.getProveedor() + " (F. " + gasto.getNumeroFactura() + ")");
            haber.setDebe(BigDecimal.ZERO);
            haber.setHaber(gasto.getImporteTotal());
            haber.setNumeroAsiento(uidAsiento);
            movContableRepo.save(haber);

            // 3. Vincular asiento al registro del gasto
            gasto.setNumeroAsiento(uidAsiento);
            gastoRepository.save(gasto);

            log.info("✅ Asiento (6->4) regenerado para: {} ({} €)", gasto.getProveedor(), gasto.getImporteTotal());
        }
    }

    /**
     * Lógica de negocio para anular un pago.
     * Esto va en el SERVICE.
     */
    @Transactional
    public void deshacerPagoGasto(Long gastoId) {
        Gasto gasto = gastoRepository.findById(gastoId)
                .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));

        if (!gasto.isPagado()) {
            return; // Si no está pagado, no hay nada que deshacer
        }

        log.info("GTI TURBO: Anulando contablemente el pago del gasto: {}", gasto.getProveedor());

        // 1. Borramos los asientos de pago asociados (PAG-...)
        // Buscamos por el concepto que genera el método 'confirmarPagoGasto'
        movContableRepo.deleteByComunidadIdAndConceptoContaining(
                gasto.getComunidad().getId(),
                "Pago Fra: " + gasto.getNumeroFactura()
        );

        // 2. Devolvemos el gasto a estado pendiente
        gasto.setPagado(false);
        gasto.setFechaPago(null);
        gastoRepository.save(gasto);

        log.info("✅ Gasto ID {} vuelto a estado PENDIENTE", gastoId);
    }

    /**
     * Método puente para compatibilidad con controladores antiguos (BancosController, etc.)
     */
    @Transactional
    public void limpiarContabilidadMesAntesDeRemesa(Long comunidadId, int mes, int anio) {
        this.limpiarContabilidadMesAntesDeRemesa(comunidadId, mes, anio, "ORDINARIA", null, true);
    }

    /**
     * Método puente para compatibilidad con ContabilidadController y BancosController
     * NOTA: He usado la 'C' mayúscula como indica tu error de compilación.
     */
    @Transactional
    public void borrarRecibosYContabilidadDelMes(Long comunidadId, int mes, int anio) {
        // Tu lógica de borrado aquí...
        log.info("GTI: Ejecutando limpieza del periodo {}/{} para la comunidad {}", mes, anio, comunidadId);
        this.borrarRecibosYcontabilidadDelMes(comunidadId, mes, anio, "ORDINARIA", null, true);
    }

    /**
     * Método puente para el registro de recibos desde procesos automáticos antiguos
     */
    @Transactional
    public Recibo registrarDevengoCuota(Vecino v, BigDecimal imp, String con, LocalDate fecha) {
        return this.registrarDevengoCuota(v, imp, con, fecha, "ORDINARIA", null);
    }
}