package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SepaXmlService {

    private static final DateTimeFormatter DATE_XML = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_XML = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Pattern DIACRITICS = Pattern.compile("[\\p{InCombiningDiacriticalMarks}]");

    public String generarPain008(
            Comunidad comunidad,
            List<Vecino> vecinos,
            LocalDate fechaCobro
    ) {
        LocalDateTime ahora = LocalDateTime.now();
        String timestamp = ahora.format(TS);

        List<AdeudoXml> adeudos = construirAdeudos(comunidad, vecinos, fechaCobro, timestamp);

        BigDecimal total = adeudos.stream()
                .map(AdeudoXml::importe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        String idAcreedor = normalizarIdentificadorAcreedor(
                comunidad.getIdentificadorAcreedor()
        );

        String nombreComunidad = limpiar(comunidad.getNombre());
        String msgId = "PRE" + timestamp + "0001" + extraerNif(idAcreedor);
        String pmtInfId = idAcreedor + "-" + timestamp + "0001";

        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>");
        xml.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.008.001.02\">");
        xml.append("<CstmrDrctDbtInitn>");

        xml.append("<GrpHdr>");
        tag(xml, "MsgId", msgId);
        tag(xml, "CreDtTm", ahora.format(DATE_TIME_XML));
        tag(xml, "NbOfTxs", String.valueOf(adeudos.size()));
        tag(xml, "CtrlSum", importe(total));

        xml.append("<InitgPty>");
        tag(xml, "Nm", nombreComunidad);
        xml.append("<Id><OrgId><Othr>");
        tag(xml, "Id", idAcreedor);
        xml.append("</Othr></OrgId></Id>");
        xml.append("</InitgPty>");

        xml.append("</GrpHdr>");

        xml.append("<PmtInf>");
        tag(xml, "PmtInfId", pmtInfId);
        tag(xml, "PmtMtd", "DD");
        tag(xml, "BtchBookg", "true");
        tag(xml, "NbOfTxs", String.valueOf(adeudos.size()));
        tag(xml, "CtrlSum", importe(total));

        xml.append("<PmtTpInf>");
        xml.append("<SvcLvl><Cd>SEPA</Cd></SvcLvl>");
        xml.append("<LclInstrm><Cd>CORE</Cd></LclInstrm>");
        tag(xml, "SeqTp", "RCUR");
        xml.append("</PmtTpInf>");

        tag(xml, "ReqdColltnDt", fechaCobro.format(DATE_XML));

        xml.append("<Cdtr>");
        tag(xml, "Nm", nombreComunidad);
        xml.append("<PstlAdr>");
        tag(xml, "Ctry", "ES");
        tag(xml, "AdrLine", limpiar(comunidad.getDireccion()));
        tag(xml, "AdrLine", direccionCpLocalidadProvincia(
                comunidad.getCodigoPostal(),
                comunidad.getPoblacion(),
                comunidad.getProvincia()
        ));
        xml.append("</PstlAdr>");
        xml.append("</Cdtr>");

        xml.append("<CdtrAcct><Id>");
        tag(xml, "IBAN", limpiarIban(comunidad.getIban()));
        xml.append("</Id><Ccy>EUR</Ccy></CdtrAcct>");

        xml.append("<CdtrAgt><FinInstnId>");
        tag(xml, "BIC", bicSeguro(comunidad.getBic()));
        xml.append("</FinInstnId></CdtrAgt>");

        tag(xml, "ChrgBr", "SLEV");

        xml.append("<CdtrSchmeId><Id><PrvtId><Othr>");
        tag(xml, "Id", idAcreedor);
        xml.append("<SchmeNm><Prtry>SEPA</Prtry></SchmeNm>");
        xml.append("</Othr></PrvtId></Id></CdtrSchmeId>");

        int contador = 1;

        for (AdeudoXml adeudo : adeudos) {
            String contador4 = String.format("%04d", contador);

            xml.append("<DrctDbtTxInf>");

            xml.append("<PmtId>");
            tag(xml, "InstrId", "-" + timestamp + "-" + contador4);
            tag(xml, "EndToEndId", timestamp + contador4);
            xml.append("</PmtId>");

            xml.append("<InstdAmt Ccy=\"EUR\">")
                    .append(importe(adeudo.importe()))
                    .append("</InstdAmt>");

            xml.append("<DrctDbtTx>");
            xml.append("<MndtRltdInf>");
            tag(xml, "MndtId", adeudo.referenciaMandato());
            tag(xml, "DtOfSgntr", "2026-01-02");
            tag(xml, "AmdmntInd", "false");
            xml.append("</MndtRltdInf>");
            xml.append("</DrctDbtTx>");

            xml.append("<DbtrAgt><FinInstnId><Othr>");
            tag(xml, "Id", "NOTPROVIDED");
            xml.append("</Othr></FinInstnId></DbtrAgt>");

            xml.append("<Dbtr>");
            tag(xml, "Nm", adeudo.nombreDeudor());
            xml.append("<PstlAdr>");
            tag(xml, "Ctry", "ES");
            tag(xml, "AdrLine", adeudo.direccion());
            tag(xml, "AdrLine", adeudo.cpLocalidadProvincia());
            xml.append("</PstlAdr>");
            xml.append("</Dbtr>");

            xml.append("<DbtrAcct><Id>");
            tag(xml, "IBAN", adeudo.iban());
            xml.append("</Id></DbtrAcct>");

            xml.append("<RmtInf>");
            tag(xml, "Ustrd", adeudo.concepto());
            xml.append("</RmtInf>");

            xml.append("</DrctDbtTxInf>");

            contador++;
        }

        xml.append("</PmtInf>");
        xml.append("</CstmrDrctDbtInitn>");
        xml.append("</Document>");
        xml.append("<!-- GENESIS 19S compatible pain.008.001.02 -->");

        return xml.toString();
    }

    private List<AdeudoXml> construirAdeudos(
            Comunidad comunidad,
            List<Vecino> vecinos,
            LocalDate fechaCobro,
            String timestamp
    ) {
        int mes = fechaCobro.getMonthValue();

        List<AdeudoXml> resultado = new ArrayList<>();

        for (Vecino vecino : vecinos) {
            if (vecino == null || !vecino.isActivo() || !vecino.isDomiciliado()) {
                continue;
            }

            List<ConceptoCobro> conceptos = vecino.getListaConceptos()
                    .stream()
                    .filter(c -> c.correspondeMes(mes))
                    .collect(Collectors.toList());

            if (conceptos.isEmpty()) {
                continue;
            }

            BigDecimal total = BigDecimal.ZERO;
            List<String> textos = new ArrayList<>();

            for (ConceptoCobro concepto : conceptos) {

                BigDecimal base = concepto.getImporte() == null
                        ? BigDecimal.ZERO
                        : concepto.getImporte();

                total = total.add(base);

//                if (concepto.getTipoImpuesto() != null
//                        && concepto.getTipoImpuesto() != TipoImpuesto.EXENTO
//                        && concepto.getPorcentajeImpuesto() != null) {
//
//                    BigDecimal impuesto = base
//                            .multiply(concepto.getPorcentajeImpuesto())
//                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
//
//                    total = total.add(impuesto);
//                }
            }

            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String concepto = textos.isEmpty()
                    ? "CUOTA COMUNIDAD VIVIENDA " + limpiar(vecino.getVivienda())
                    : String.join(" / ", textos);

            resultado.add(new AdeudoXml(
                    limpiar(vecino.getNombre()),
                    limpiarIban(vecino.getIban()),
                    referenciaMandato(comunidad, vecino),
                    total.setScale(2, RoundingMode.HALF_UP),
                    direccionVecino(comunidad, vecino),
                    direccionCpLocalidadProvincia(
                            vecino.getCodigopostal(),
                            valorPreferente(vecino.getPoblacion(), comunidad.getPoblacion()),
                            valorPreferente(vecino.getProvincia(), comunidad.getProvincia())
                    ),
                    limitar(concepto, 140)
            ));
        }

        return resultado;
    }

    private String referenciaMandato(Comunidad comunidad, Vecino vecino) {
        String ref = limpiar(vecino.getReferenciaMandato());

        if (!ref.isBlank()) {
            return ref;
        }

        String nifComunidad = extraerNif(
                normalizarIdentificadorAcreedor(
                        comunidad.getIdentificadorAcreedor()
                )
        );

        return limitar(
                nifComunidad + "000000000000000000" + limpiar(vecino.getNif()),
                35
        );
    }

    private String direccionVecino(Comunidad comunidad, Vecino vecino) {
        String dir = valorPreferente(vecino.getDireccion(), comunidad.getDireccion());

        if (vecino.getVivienda() != null
                && !vecino.getVivienda().isBlank()
                && !dir.contains(vecino.getVivienda())) {
            dir = dir + " " + vecino.getVivienda();
        }

        return limpiar(dir);
    }

    private String normalizarIdentificadorAcreedor(String id) {
        String limpio = limpiar(id)
                .replace("-", "")
                .replace(" ", "");

        if (limpio.length() >= 4 && limpio.startsWith("ES")) {
            return limpio;
        }

        return limpio;
    }

    private String extraerNif(String identificador) {
        String limpio = limpiar(identificador)
                .replace("-", "")
                .replace(" ", "");

        if (limpio.length() > 7) {
            return limpio.substring(limpio.length() - 9);
        }

        return limpio;
    }

    private String direccionCpLocalidadProvincia(
            String cp,
            String poblacion,
            String provincia
    ) {
        String texto = limpiar(cp) + " " + limpiar(poblacion) + " " + limpiar(provincia);
        return texto.trim().replaceAll("\\s+", " ");
    }

    private String bicSeguro(String bic) {
        String limpio = limpiar(bic).replace(" ", "");

        if (limpio.isBlank()) {
            return "NOTPROVIDED";
        }

        return limpio;
    }

    private String limpiarIban(String iban) {
        return limpiar(iban).replace(" ", "");
    }

    private String valorPreferente(String principal, String alternativo) {
        if (principal != null && !principal.isBlank()) {
            return principal;
        }

        return alternativo == null ? "" : alternativo;
    }

    private void tag(StringBuilder xml, String tag, String value) {
        xml.append("<")
                .append(tag)
                .append(">")
                .append(escape(value))
                .append("</")
                .append(tag)
                .append(">");
    }

    private String importe(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String limpiar(String texto) {
        if (texto == null) {
            return "";
        }

        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD);
        normalizado = DIACRITICS.matcher(normalizado).replaceAll("");

        return normalizado
                .replace("Ñ", "N")
                .replace("ñ", "n")
                .trim();
    }

    private String limitar(String texto, int max) {
        String limpio = limpiar(texto);

        if (limpio.length() <= max) {
            return limpio;
        }

        return limpio.substring(0, max);
    }

    private String escape(String texto) {
        return limpiar(texto)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record AdeudoXml(
            String nombreDeudor,
            String iban,
            String referenciaMandato,
            BigDecimal importe,
            String direccion,
            String cpLocalidadProvincia,
            String concepto
    ) {
    }
}