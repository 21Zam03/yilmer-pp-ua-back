package com.zam.sunat_service.integration;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.entity.VentaSireEntity;
import com.zam.sunat_service.exceptions.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Convierte el ZIP de la propuesta RVIE (servicio 5.17) en entidades VentaSireEntity, todo en memoria.
 * <p>
 * El TXT viene separado por "|" con una cabecera. Las columnas se ubican por el nombre de la cabecera
 * y, si no se reconoce, por la posición estándar del formato de exportación del RVIE:
 * 0 RUC | 1 Razón social | 2 Periodo | 3 CAR SUNAT | 4 Fecha de emisión | 5 Fecha Vcto/Pago |
 * 6 Tipo CP/Doc. | 7 Serie del CDP | 8 Nro CP o Doc. | 9 Nro final | 10 Tipo Doc Identidad |
 * 11 Nro Doc Identidad | 12 Apellidos y nombres / Razón social | 13 Valor exportación |
 * 14 BI Gravada | 15 Dscto BI | 16 IGV / IPM | ... | 25 Total CP | 26 Moneda | ...
 */
@Slf4j
@Component
public class SireVentasTxtParser {

    // Protección ante un ZIP malformado o malicioso: el TXT se lee completo en memoria.
    private static final long MAX_TXT_BYTES = 200L * 1024 * 1024;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE
    );

    private enum Columna {
        CAR(3, "carsunat", "car"),
        FECHA_EMISION(4, "fechadeemision", "fechaemision", "fecemision"),
        TIPO_COMPROBANTE(6, "tipocpdoc", "tipocp", "tipocomprobante"),
        SERIE(7, "seriedelcdp", "seriecdp", "serie"),
        NUMERO(8, "nrocpodocnroinicialrango", "nrocpodoc", "nrocp", "numero"),
        CLIENTE_TIPO_DOC(10, "tipodocidentidad"),
        CLIENTE_NRO_DOC(11, "nrodocidentidad"),
        CLIENTE_NOMBRE(12, "apellidosnombresrazonsocial", "apellidosynombresrazonsocial", "razonsocialcliente"),
        BASE_IMPONIBLE(14, "bigravada", "baseimponible"),
        IGV(16, "igvipm", "igv"),
        IMPORTE_TOTAL(25, "totalcp", "importetotal");

        private final int posicionPorDefecto;
        private final List<String> nombres;

        Columna(int posicionPorDefecto, String... nombres) {
            this.posicionPorDefecto = posicionPorDefecto;
            this.nombres = List.of(nombres);
        }
    }

    public List<VentaSireEntity> parsear(byte[] archivo, ClientEntity client, String periodo) {
        log.info("SIRE archivo {} {}: {} bytes | hex inicial: {} | texto inicial: {}",
                client.getRuc(), periodo, archivo.length, hexInicial(archivo), vistaPrevia(archivo));

        List<String> lineas = leerLineas(obtenerTxt(archivo, 0));
        logContenidoTxt(lineas, client.getRuc(), periodo);
        if (lineas.isEmpty()) {
            return List.of();
        }

        Map<Columna, Integer> posiciones = resolverPosiciones(lineas.get(0));
        int columnasMinimas = Collections.max(posiciones.values()) + 1;

        // Por CAR: si SUNAT repitiera un comprobante, se queda la última aparición.
        Map<String, VentaSireEntity> ventas = new LinkedHashMap<>();
        int descartadas = 0;
        for (int i = 1; i < lineas.size(); i++) {
            String linea = lineas.get(i);
            if (linea.isBlank()) {
                continue;
            }
            String[] campos = linea.split("\\|", -1);
            String car = texto(campos, posiciones.get(Columna.CAR));
            if (campos.length < columnasMinimas || car == null) {
                descartadas++;
                log.warn("SIRE ventas {} {}: línea {} descartada (columnas={}, CAR={})",
                        client.getRuc(), periodo, i + 1, campos.length, car);
                continue;
            }
            ventas.put(car, mapear(campos, posiciones, car, client, periodo, i + 1));
        }

        if (descartadas > 0) {
            log.warn("SIRE ventas {} {}: {} líneas descartadas de {}", client.getRuc(), periodo, descartadas, lineas.size() - 1);
        }
        return new ArrayList<>(ventas.values());
    }

    private VentaSireEntity mapear(String[] campos, Map<Columna, Integer> pos, String car,
                                   ClientEntity client, String periodo, int numeroLinea) {
        VentaSireEntity venta = new VentaSireEntity();
        venta.setClient(client);
        venta.setPeriodo(periodo);
        venta.setCarSunat(car);
        venta.setFechaEmision(fecha(texto(campos, pos.get(Columna.FECHA_EMISION)), numeroLinea));
        venta.setTipoComprobante(recortar(texto(campos, pos.get(Columna.TIPO_COMPROBANTE)), 4));
        venta.setSerie(recortar(texto(campos, pos.get(Columna.SERIE)), 20));
        venta.setNumero(recortar(texto(campos, pos.get(Columna.NUMERO)), 20));
        venta.setClienteTipoDoc(recortar(texto(campos, pos.get(Columna.CLIENTE_TIPO_DOC)), 4));
        venta.setClienteNroDoc(recortar(texto(campos, pos.get(Columna.CLIENTE_NRO_DOC)), 20));
        venta.setClienteNombre(recortar(texto(campos, pos.get(Columna.CLIENTE_NOMBRE)), 255));
        venta.setBaseImponible(monto(texto(campos, pos.get(Columna.BASE_IMPONIBLE)), numeroLinea));
        venta.setIgv(monto(texto(campos, pos.get(Columna.IGV)), numeroLinea));
        venta.setImporteTotal(monto(texto(campos, pos.get(Columna.IMPORTE_TOTAL)), numeroLinea));
        return venta;
    }

    /**
     * Lo esperado es un ZIP, pero se aceptan otras formas en que SUNAT puede entregar el archivo:
     * ZIP en Base64, gzip o el TXT plano. Si no se reconoce, el error incluye el inicio del contenido.
     */
    private byte[] obtenerTxt(byte[] contenido, int profundidad) {
        if (empiezaCon(contenido, 0x50, 0x4B)) { // "PK"
            return extraerTxt(contenido);
        }
        if (empiezaCon(contenido, 0x1F, 0x8B)) {
            return obtenerTxt(gunzip(contenido), profundidad + 1);
        }

        String texto = new String(contenido, StandardCharsets.ISO_8859_1).strip();
        if (texto.startsWith("{") || texto.startsWith("[") || texto.startsWith("<")) {
            throw new BusinessException("SUNAT devolvió " + (texto.startsWith("<") ? "HTML" : "JSON")
                    + " en lugar del archivo: " + vistaPrevia(contenido));
        }
        if (profundidad == 0 && BASE64.matcher(texto).matches()) {
            try {
                return obtenerTxt(Base64.getMimeDecoder().decode(texto), profundidad + 1);
            } catch (IllegalArgumentException ignored) {
                // no era Base64; se evalúa como texto
            }
        }
        if (texto.contains("|")) {
            log.info("SIRE: el archivo descargado es un TXT sin comprimir");
            return contenido;
        }
        throw new BusinessException("El archivo descargado de SUNAT no tiene un formato reconocido ("
                + contenido.length + " bytes): " + vistaPrevia(contenido));
    }

    private static final java.util.regex.Pattern BASE64 = java.util.regex.Pattern.compile("^[A-Za-z0-9+/=\\r\\n]+$");

    private boolean empiezaCon(byte[] contenido, int b0, int b1) {
        return contenido.length >= 2 && (contenido[0] & 0xFF) == b0 && (contenido[1] & 0xFF) == b1;
    }

    private byte[] gunzip(byte[] contenido) {
        try (var gzip = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(contenido))) {
            return gzip.readNBytes((int) MAX_TXT_BYTES);
        } catch (IOException e) {
            throw new BusinessException("No se pudo descomprimir el archivo (gzip) de SUNAT: " + e.getMessage());
        }
    }

    // Primeros 200 caracteres legibles, para diagnosticar qué devolvió SUNAT.
    private String vistaPrevia(byte[] contenido) {
        String texto = new String(contenido, 0, Math.min(contenido.length, 200), StandardCharsets.ISO_8859_1);
        return "\"" + texto.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "?").replaceAll("\\s+", " ") + "\"";
    }

    /**
     * SUNAT antepone el marcador de "ZIP dividido" (PK 07 08) aunque el ZIP es de una sola parte.
     * ZipInputStream exige que el archivo empiece con la cabecera local (PK 03 04); si no, no ve entradas.
     * Por eso se lee desde la primera cabecera local.
     */
    private int inicioPrimeraEntrada(byte[] zip) {
        for (int i = 0; i + 3 < zip.length && i < 1024; i++) {
            if (zip[i] == 0x50 && zip[i + 1] == 0x4B && zip[i + 2] == 0x03 && zip[i + 3] == 0x04) {
                return i;
            }
        }
        return 0;
    }

    private byte[] extraerTxt(byte[] zip) {
        int inicio = inicioPrimeraEntrada(zip);
        if (inicio > 0) {
            log.info("SIRE ZIP: se omiten {} byte(s) previos a la primera entrada ({})", inicio,
                    hexInicial(Arrays.copyOf(zip, inicio)));
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip, inicio, zip.length - inicio))) {
            ZipEntry entry;
            int entradas = 0;
            byte[] txt = null;
            // Recorre todo el ZIP para dejar en el log todas las entradas, aunque solo se use el primer .txt.
            while ((entry = zis.getNextEntry()) != null) {
                entradas++;
                boolean esTxt = !entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".txt");
                if (esTxt && txt == null) {
                    txt = leerEntrada(zis, entry.getName());
                    log.info("SIRE ZIP entrada {}: {} ({} bytes descomprimido) <- se usa esta", entradas, entry.getName(), txt.length);
                } else {
                    log.info("SIRE ZIP entrada {}: {}{} (tamaño declarado {} bytes)", entradas, entry.getName(),
                            entry.isDirectory() ? " [carpeta]" : "", entry.getSize());
                }
            }
            log.info("SIRE ZIP: {} entrada(s) en total", entradas);
            if (txt != null) {
                return txt;
            }
            throw new BusinessException(entradas > 0
                    ? "El ZIP descargado de SUNAT no contiene un archivo .txt"
                    : "El archivo descargado de SUNAT no es un ZIP válido: " + vistaPrevia(zip));
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el ZIP descargado de SUNAT: " + e.getMessage());
        }
    }

    private byte[] leerEntrada(ZipInputStream zis, String nombre) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int leidos;
        while ((leidos = zis.read(buffer)) != -1) {
            total += leidos;
            if (total > MAX_TXT_BYTES) {
                throw new BusinessException("El archivo " + nombre + " supera el tamaño máximo permitido");
            }
            out.write(buffer, 0, leidos);
        }
        return out.toByteArray();
    }

    // SUNAT suele generar UTF-8, pero algunos archivos vienen en ISO-8859-1 (tildes y Ñ en razones sociales).
    private List<String> leerLineas(byte[] txt) {
        String contenido;
        try {
            contenido = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(txt))
                    .toString();
        } catch (CharacterCodingException e) {
            contenido = new String(txt, StandardCharsets.ISO_8859_1);
        }
        if (contenido.startsWith("﻿")) {
            contenido = contenido.substring(1);
        }
        return contenido.lines().toList();
    }

    // Diagnóstico: cabecera + primeras filas del TXT. Contiene datos de terceros (DNI/RUC, nombres, montos);
    // bajar a DEBUG cuando el formato esté validado.
    private static final int LINEAS_LOG = 5;

    private void logContenidoTxt(List<String> lineas, String ruc, String periodo) {
        log.info("SIRE TXT {} {}: {} línea(s) incluida la cabecera", ruc, periodo, lineas.size());
        for (int i = 0; i < Math.min(lineas.size(), LINEAS_LOG + 1); i++) {
            String linea = lineas.get(i);
            log.info("SIRE TXT línea {} ({} columnas): {}", i + 1, linea.split("\\|", -1).length, linea);
        }
        if (lineas.size() > LINEAS_LOG + 1) {
            log.info("SIRE TXT ... {} línea(s) más", lineas.size() - LINEAS_LOG - 1);
        }
    }

    private String hexInicial(byte[] contenido) {
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < Math.min(contenido.length, 16); i++) {
            hex.append(String.format("%02X ", contenido[i]));
        }
        return hex.toString().trim();
    }

    private Map<Columna, Integer> resolverPosiciones(String cabecera) {
        List<String> nombres = Arrays.stream(cabecera.split("\\|", -1)).map(this::normalizar).toList();
        Map<Columna, Integer> posiciones = new EnumMap<>(Columna.class);
        for (Columna columna : Columna.values()) {
            int posicion = columna.nombres.stream()
                    .mapToInt(nombres::indexOf)
                    .filter(i -> i >= 0)
                    .findFirst()
                    .orElse(columna.posicionPorDefecto);
            posiciones.put(columna, posicion);
        }
        return posiciones;
    }

    // "Fecha de emisión" -> "fechadeemision"; "IGV / IPM" -> "igvipm".
    private String normalizar(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    // Vacío o guion = sin dato.
    private String texto(String[] campos, int posicion) {
        if (posicion >= campos.length) {
            return null;
        }
        String valor = campos[posicion].trim();
        return valor.isEmpty() || valor.equals("-") ? null : valor;
    }

    private String recortar(String valor, int max) {
        return valor == null || valor.length() <= max ? valor : valor.substring(0, max);
    }

    // Montos sin dato = 0, para que los totales del periodo cuadren.
    private BigDecimal monto(String valor, int numeroLinea) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(valor.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new BusinessException("Monto inválido en la línea " + numeroLinea + " del archivo de SUNAT: " + valor);
        }
    }

    private LocalDate fecha(String valor, int numeroLinea) {
        if (valor == null) {
            return null;
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(valor, format);
            } catch (DateTimeParseException ignored) {
                // se prueba el siguiente formato
            }
        }
        throw new BusinessException("Fecha inválida en la línea " + numeroLinea + " del archivo de SUNAT: " + valor);
    }

}
