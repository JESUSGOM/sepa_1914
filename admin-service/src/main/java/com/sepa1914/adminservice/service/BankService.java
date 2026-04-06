package com.sepa1914.adminservice.service;

import org.iban4j.IbanUtil;
import org.iban4j.IbanFormatException;
import org.iban4j.InvalidCheckDigitException;
import org.iban4j.UnsupportedCountryException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class BankService {

    private static class DatosBanco {
        String bic;
        String nombre;

        DatosBanco(String bic, String nombre) {
            this.bic = bic;
            this.nombre = nombre;
        }
    }

    private static final Map<String, DatosBanco> BANCO_DATA_MAP = new HashMap<>();

    static {
        BANCO_DATA_MAP.put("2100", new DatosBanco("CAIXESMMXXX", "CAIXABANK"));
        BANCO_DATA_MAP.put("0049", new DatosBanco("BSCHESMMXXX", "BANCO SANTANDER"));
        BANCO_DATA_MAP.put("0182", new DatosBanco("BBVAESMMXXX", "BBVA"));
        BANCO_DATA_MAP.put("0081", new DatosBanco("BSABESBBXXX", "BANCO SABADELL"));
        BANCO_DATA_MAP.put("3085", new DatosBanco("CASPESMMXXX", "CAJA RURAL DEL SUR"));
        BANCO_DATA_MAP.put("2103", new DatosBanco("UCJAES2MXXX", "UNICAJA BANCO"));
        BANCO_DATA_MAP.put("0128", new DatosBanco("BKBKESMMXXX", "BANKINTER"));
        BANCO_DATA_MAP.put("0241", new DatosBanco("AYGBESMMXXX", "A&G BANCO"));
        BANCO_DATA_MAP.put("2080", new DatosBanco("CAGLESMMXXX", "ABANCA"));
        BANCO_DATA_MAP.put("1535", new DatosBanco("BPROES2MXXX", "BANCO PROXIMO"));
        BANCO_DATA_MAP.put("0011", new DatosBanco("ALLFESMMXXX", "ALLFUNDS BANK"));
        BANCO_DATA_MAP.put("0200", new DatosBanco("PRVBESB1XXX", "BANCO DEGROOF PETERCAM"));
        BANCO_DATA_MAP.put("0136", new DatosBanco("AREBESMMXXX", "ARESBANK"));
        BANCO_DATA_MAP.put("3183", new DatosBanco("CASDESBBXXX", "ARQUIA BANK"));
        BANCO_DATA_MAP.put("1541", new DatosBanco("BNPAESMMXXX", "BNP PARIBAS"));
        BANCO_DATA_MAP.put("0061", new DatosBanco("BMARES2MXXX", "BANCA MARCH"));
        BANCO_DATA_MAP.put("1550", new DatosBanco("ADBKESMMXXX", "ADVANZIA BANK"));
        BANCO_DATA_MAP.put("0078", new DatosBanco("BAPUES22XXX", "BANCO PUEYO"));
        BANCO_DATA_MAP.put("0188", new DatosBanco("ALCLESMMXXX", "BANCO ALCALA"));
        BANCO_DATA_MAP.put("0225", new DatosBanco("FIEIESM1XXX", "BANCO CETELEM"));
        BANCO_DATA_MAP.put("0198", new DatosBanco("BCOEESMMXXX", "BANCO COOPERATIVO ESPAÑOL"));
        BANCO_DATA_MAP.put("0091", new DatosBanco("BDEWESMMXXX", "BANCO DE ALBACETE"));
        BANCO_DATA_MAP.put("0240", new DatosBanco("BCCAESMMXXX", "BANCO DE CREDITO SOCIAL COOPERATIVO"));
        BANCO_DATA_MAP.put("0003", new DatosBanco("BDEPESM1XXX", "BANCO DE DEPOSITOS"));
        BANCO_DATA_MAP.put("9000", new DatosBanco("ESPBESMMXXX", "BANCO DE ESPAÑA"));
        BANCO_DATA_MAP.put("1569", new DatosBanco("BARCESMMXXX", "BARCLAYS BANK"));
        BANCO_DATA_MAP.put("0169", new DatosBanco("NACNESMMXXX", "BANCO DE LA NACION ARGENTINA"));
        BANCO_DATA_MAP.put("0220", new DatosBanco("FIOFESM1XXX", "BANCO FINANTIA"));
        BANCO_DATA_MAP.put("0232", new DatosBanco("INVLESMMXXX", "BANCO INVERSIS"));
        BANCO_DATA_MAP.put("0186", new DatosBanco("BFIVESBBXXX", "BANCO MEDIOLANUM"));
        BANCO_DATA_MAP.put("0121", new DatosBanco("OCBAESM1XXX", "BANCO OCCIDENTAL"));
        BANCO_DATA_MAP.put("0235", new DatosBanco("PICWESMMXXX", "BANCO PICHINCHA"));
        BANCO_DATA_MAP.put("1509", new DatosBanco("MYBRESMMXXX", "MYINVESTOR"));
        BANCO_DATA_MAP.put("1574", new DatosBanco("REVOLUTXXX", "REVOLUT BANK"));
        BANCO_DATA_MAP.put("0219", new DatosBanco("RENTESMMXXX", "RENTA 4 BANCO"));
        BANCO_DATA_MAP.put("0152", new DatosBanco("BARCESMMXXX", "BARCLAYS"));
        BANCO_DATA_MAP.put("1554", new DatosBanco("SOLFESMMXXX", "SOLARIS SE"));
        BANCO_DATA_MAP.put("1533", new DatosBanco("ELVIESMMXXX", "ELAVON FINANCIAL"));
        BANCO_DATA_MAP.put("1492", new DatosBanco("BNCZESMMXXX", "BANCO CTT"));
        BANCO_DATA_MAP.put("0149", new DatosBanco("BNPAESMMXXX", "BNP PARIBAS S.A."));
        BANCO_DATA_MAP.put("1500", new DatosBanco("FSCHESMMXXX", "FERRATUM BANK"));
        BANCO_DATA_MAP.put("1587", new DatosBanco("BNCYESMMXXX", "BUNQ B.V."));
        BANCO_DATA_MAP.put("1576", new DatosBanco("N26EESMMXXX", "N26 BANK"));
        BANCO_DATA_MAP.put("8640", new DatosBanco("SANTIESMMXXX", "SANTANDER CONSUMER"));
        BANCO_DATA_MAP.put("1545", new DatosBanco("VIVFESMMXXX", "VIVID MONEY"));
        BANCO_DATA_MAP.put("0038", new DatosBanco("BCOEESMMXXX", "BANCO PASTOR"));
        BANCO_DATA_MAP.put("1451", new DatosBanco("STBLESMMXXX", "SAXO BANK"));
        BANCO_DATA_MAP.put("1493", new DatosBanco("ALPHESMMXXX", "ALPHABET"));
        BANCO_DATA_MAP.put("3025", new DatosBanco("CDENESBBXXX", "CAIXA D'ENGINYERS"));
        BANCO_DATA_MAP.put("3035", new DatosBanco("CLPEES2MXXX", "LABORAL KUTXA"));
        BANCO_DATA_MAP.put("3058", new DatosBanco("CCRIES2AXXX", "CAJAMAR"));
        BANCO_DATA_MAP.put("1465", new DatosBanco("INGDESMMXXX", "ING BANK"));
        BANCO_DATA_MAP.put("0073", new DatosBanco("OPENESMMXXX", "OPENBANK"));
        BANCO_DATA_MAP.put("1491", new DatosBanco("TRDBE22XXXX", "TRIODOS BANK"));
    }

    public void validarIban(String iban) throws Exception {
        if (iban == null || iban.trim().isEmpty()) throw new Exception("El IBAN no puede estar vacío");
        try {
            IbanUtil.validate(iban.replace(" ", ""));
        } catch (IbanFormatException | InvalidCheckDigitException | UnsupportedCountryException e) {
            throw new Exception("El formato del IBAN es incorrecto: " + e.getMessage());
        }
    }

    public String obtenerBicDesdeIban(String iban) {
        String codigo = extraerCodigoEntidad(iban);
        return BANCO_DATA_MAP.containsKey(codigo) ? BANCO_DATA_MAP.get(codigo).bic : "";
    }

    public String obtenerNombreBanco(String iban) {
        String codigo = extraerCodigoEntidad(iban);
        return BANCO_DATA_MAP.containsKey(codigo) ? BANCO_DATA_MAP.get(codigo).nombre : "ENTIDAD NO IDENTIFICADA";
    }

    private String extraerCodigoEntidad(String iban) {
        if (iban == null || iban.length() < 8) return "";
        return iban.replace(" ", "").toUpperCase().substring(4, 8);
    }
}