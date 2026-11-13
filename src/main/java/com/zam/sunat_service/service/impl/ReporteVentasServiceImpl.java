package com.zam.sunat_service.service.impl;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.entity.TipoReporteVentas;
import com.zam.sunat_service.entity.VentaSireEntity;
import com.zam.sunat_service.exceptions.BusinessException;
import com.zam.sunat_service.exceptions.NotFoundException;
import com.zam.sunat_service.repository.ClientRepository;
import com.zam.sunat_service.repository.VentaSireRepository;
import com.zam.sunat_service.response.ReporteExcel;
import com.zam.sunat_service.service.ReporteVentasService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ReporteVentasServiceImpl implements ReporteVentasService {

    private static final Pattern RUC = Pattern.compile("^(10|15|17|20)\\d{9}$");
    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale ES_PE = Locale.forLanguageTag("es-PE");
    private static final String[] MESES = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto",
            "Setiembre", "Octubre", "Noviembre", "Diciembre"};
    private static final Map<String, String> TIPOS_COMPROBANTE = Map.of(
            "01", "Factura", "03", "Boleta", "07", "Nota de crédito", "08", "Nota de débito", "12", "Ticket");
    private static final Map<String, String> TIPOS_DOCUMENTO = Map.of(
            "0", "Doc. trib. no domic.", "1", "DNI", "4", "Carné de extranjería", "6", "RUC", "7", "Pasaporte",
            "A", "Céd. diplomática");

    private static final String[] COLUMNAS = {"Fecha emisión", "Tipo", "Serie", "Número", "Tipo doc.", "N° documento",
            "Comprador", "Base imponible", "IGV", "Importe total"};
    private static final int[] ANCHOS = {14, 16, 10, 12, 14, 16, 40, 16, 14, 16};
    private static final int PRIMERA_COLUMNA_MONTO = 7;
    private static final String[] COLUMNAS_RESUMEN = {"Fecha", "Día", "Comprobantes", "Base imponible", "IGV", "Importe total"};
    private static final int[] ANCHOS_RESUMEN = {14, 12, 14, 16, 14, 16};

    private final ClientRepository clientRepository;
    private final VentaSireRepository ventaRepository;
    private final Clock clock;

    @Autowired
    public ReporteVentasServiceImpl(ClientRepository clientRepository, VentaSireRepository ventaRepository) {
        this(clientRepository, ventaRepository, Clock.system(ZoneId.of("America/Lima")));
    }

    // Para tests: permite fijar la fecha de generación.
    ReporteVentasServiceImpl(ClientRepository clientRepository, VentaSireRepository ventaRepository, Clock clock) {
        this.clientRepository = clientRepository;
        this.ventaRepository = ventaRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ReporteExcel exportarVentas(String ruc, String periodo, TipoReporteVentas tipo) {
        String rucLimpio = ruc == null ? "" : ruc.trim();
        String periodoLimpio = periodo == null ? "" : periodo.trim();
        if (!RUC.matcher(rucLimpio).matches()) {
            throw new BusinessException("El RUC debe tener 11 dígitos y empezar con 10, 15, 17 o 20");
        }
        YearMonth mes;
        try {
            mes = YearMonth.parse(periodoLimpio, PERIODO);
        } catch (RuntimeException e) {
            throw new BusinessException("El periodo debe tener el formato yyyyMM (ej. 202610)");
        }
        ClientEntity client = clientRepository.findByRuc(rucLimpio)
                .orElseThrow(() -> new NotFoundException("No existe un cliente con el RUC " + rucLimpio));

        List<VentaSireEntity> ventas = ventaRepository.findByClientAndPeriodo(client, periodoLimpio).stream()
                .filter(tipo::incluye)
                .sorted(ORDEN)
                .toList();
        if (ventas.isEmpty()) {
            throw new BusinessException("No hay " + tipo.getNombreArchivo().toLowerCase(ES_PE) + " sincronizadas de "
                    + nombreMes(mes) + " para el RUC " + rucLimpio + ". Sincroniza el periodo primero.");
        }

        byte[] contenido = generar(client, mes, tipo, ventas);
        String nombre = tipo.getNombreArchivo() + "_" + rucLimpio + "_" + periodoLimpio + ".xlsx";
        return new ReporteExcel(nombre, contenido);
    }

    // Fecha, tipo, serie y número numérico (como texto "10" quedaría antes que "2").
    private static final Comparator<VentaSireEntity> ORDEN = Comparator
            .comparing(VentaSireEntity::getFechaEmision, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(VentaSireEntity::getTipoComprobante, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(VentaSireEntity::getSerie, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(v -> numero(v.getNumero()));

    private static long numero(String numero) {
        try {
            return numero == null ? Long.MAX_VALUE : Long.parseLong(numero.trim());
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private byte[] generar(ClientEntity client, YearMonth mes, TipoReporteVentas tipo, List<VentaSireEntity> ventas) {
        // Streaming: mantiene en memoria solo una ventana de filas, para periodos con miles de comprobantes.
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(workbook);
            hojaComprobantes(workbook, estilos, client, mes, tipo, ventas);
            hojaResumen(workbook, estilos, client, mes, tipo, ventas);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel", e);
        } finally {
            workbook.dispose(); // borra los archivos temporales del streaming
        }
    }

    private void hojaComprobantes(SXSSFWorkbook workbook, Estilos e, ClientEntity client, YearMonth mes,
                                  TipoReporteVentas tipo, List<VentaSireEntity> ventas) {
        Sheet sheet = workbook.createSheet("Comprobantes");
        int fila = encabezado(sheet, e, client, mes, tipo.getDescripcion(), COLUMNAS.length);

        int filaTitulos = fila;
        titulos(sheet.createRow(fila++), e, COLUMNAS);
        int primeraFilaDatos = fila;

        for (VentaSireEntity v : ventas) {
            Row row = sheet.createRow(fila++);
            fecha(row, 0, v.getFechaEmision(), e);
            texto(row, 1, TIPOS_COMPROBANTE.getOrDefault(v.getTipoComprobante(), "Tipo " + v.getTipoComprobante()), e.texto);
            texto(row, 2, v.getSerie(), e.texto);
            texto(row, 3, v.getNumero(), e.texto);
            texto(row, 4, v.getClienteNroDoc() == null ? "" : TIPOS_DOCUMENTO.getOrDefault(v.getClienteTipoDoc(), "Otro"), e.texto);
            texto(row, 5, v.getClienteNroDoc(), e.texto);
            texto(row, 6, v.getClienteNombre() != null ? v.getClienteNombre() : "Sin nombre", e.texto);
            monto(row, 7, v.getBaseImponible(), e.monto);
            monto(row, 8, v.getIgv(), e.monto);
            monto(row, 9, v.getImporteTotal(), e.monto);
        }
        int ultimaFilaDatos = fila - 1;

        Row total = sheet.createRow(fila);
        texto(total, 0, "Total (" + ventas.size() + " comprobantes)", e.total);
        for (int c = 1; c < PRIMERA_COLUMNA_MONTO; c++) {
            texto(total, c, "", e.total);
        }
        for (int c = PRIMERA_COLUMNA_MONTO; c < COLUMNAS.length; c++) {
            suma(total, c, primeraFilaDatos, ultimaFilaDatos, e.totalMonto);
        }

        anchos(sheet, ANCHOS);
        sheet.createFreezePane(0, filaTitulos + 1);
        sheet.setAutoFilter(new CellRangeAddress(filaTitulos, ultimaFilaDatos, 0, COLUMNAS.length - 1));
    }

    private void hojaResumen(SXSSFWorkbook workbook, Estilos e, ClientEntity client, YearMonth mes,
                             TipoReporteVentas tipo, List<VentaSireEntity> ventas) {
        Sheet sheet = workbook.createSheet("Resumen por día");
        int fila = encabezado(sheet, e, client, mes, tipo.getDescripcion() + " · resumen por día", COLUMNAS_RESUMEN.length);

        // Todos los días del mes (incluso sin ventas) más cualquier fecha de otro mes anotada en el periodo.
        TreeMap<LocalDate, Acumulado> porDia = new TreeMap<>();
        for (LocalDate d = mes.atDay(1); !d.isAfter(mes.atEndOfMonth()); d = d.plusDays(1)) {
            porDia.put(d, new Acumulado());
        }
        Acumulado sinFecha = new Acumulado();
        for (VentaSireEntity v : ventas) {
            Acumulado a = v.getFechaEmision() == null ? sinFecha : porDia.computeIfAbsent(v.getFechaEmision(), d -> new Acumulado());
            a.sumar(v);
        }

        int filaTitulos = fila;
        titulos(sheet.createRow(fila++), e, COLUMNAS_RESUMEN);
        int primeraFilaDatos = fila;
        for (Map.Entry<LocalDate, Acumulado> dia : porDia.entrySet()) {
            Row row = sheet.createRow(fila++);
            boolean vacio = dia.getValue().cantidad == 0;
            fecha(row, 0, dia.getKey(), e);
            String nombreDia = dia.getKey().getDayOfWeek().getDisplayName(TextStyle.FULL, ES_PE);
            texto(row, 1, Character.toUpperCase(nombreDia.charAt(0)) + nombreDia.substring(1), vacio ? e.textoTenue : e.texto);
            dia.getValue().escribir(row, 2, e, vacio);
        }
        if (sinFecha.cantidad > 0) {
            Row row = sheet.createRow(fila++);
            texto(row, 0, "Sin fecha", e.texto);
            texto(row, 1, "", e.texto);
            sinFecha.escribir(row, 2, e, false);
        }
        int ultimaFilaDatos = fila - 1;

        Row total = sheet.createRow(fila);
        texto(total, 0, "Total", e.total);
        texto(total, 1, "", e.total);
        suma(total, 2, primeraFilaDatos, ultimaFilaDatos, e.totalEntero);
        for (int c = 3; c < COLUMNAS_RESUMEN.length; c++) {
            suma(total, c, primeraFilaDatos, ultimaFilaDatos, e.totalMonto);
        }

        anchos(sheet, ANCHOS_RESUMEN);
        sheet.createFreezePane(0, filaTitulos + 1);
    }

    // Razón social, RUC, periodo, contenido y fecha de generación. Devuelve la fila donde van los títulos.
    private int encabezado(Sheet sheet, Estilos e, ClientEntity client, YearMonth mes, String contenido, int columnas) {
        Row titulo = sheet.createRow(0);
        texto(titulo, 0, client.getBusinessName(), e.titulo);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columnas - 1));
        String[][] datos = {
                {"RUC", client.getRuc()},
                {"Periodo", nombreMes(mes)},
                {"Contenido", contenido},
                {"Generado", LocalDateTime.now(clock).format(FECHA_HORA) + " · Fuente: propuesta RVIE del SIRE (SUNAT)"},
        };
        for (int i = 0; i < datos.length; i++) {
            Row row = sheet.createRow(i + 1);
            texto(row, 0, datos[i][0], e.etiqueta);
            texto(row, 1, datos[i][1], e.texto);
            sheet.addMergedRegion(new CellRangeAddress(i + 1, i + 1, 1, columnas - 1));
        }
        return datos.length + 2; // una fila en blanco antes de los títulos
    }

    private static String nombreMes(YearMonth mes) {
        return MESES[mes.getMonthValue() - 1] + " " + mes.getYear();
    }

    private static void titulos(Row row, Estilos e, String[] columnas) {
        for (int c = 0; c < columnas.length; c++) {
            texto(row, c, columnas[c], e.cabecera);
        }
    }

    private static void texto(Row row, int col, String valor, CellStyle estilo) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor == null ? "" : valor);
        cell.setCellStyle(estilo);
    }

    private static void fecha(Row row, int col, LocalDate valor, Estilos e) {
        Cell cell = row.createCell(col);
        if (valor != null) {
            cell.setCellValue(valor);
        }
        cell.setCellStyle(e.fecha);
    }

    private static void monto(Row row, int col, BigDecimal valor, CellStyle estilo) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor == null ? 0d : valor.doubleValue());
        cell.setCellStyle(estilo);
    }

    // SUM con fórmula: si alguien corrige una celda en Excel, el total se recalcula.
    private static void suma(Row row, int col, int primeraFila, int ultimaFila, CellStyle estilo) {
        Cell cell = row.createCell(col);
        String letra = org.apache.poi.ss.util.CellReference.convertNumToColString(col);
        cell.setCellFormula("SUM(" + letra + (primeraFila + 1) + ":" + letra + (ultimaFila + 1) + ")");
        cell.setCellStyle(estilo);
    }

    private static void anchos(Sheet sheet, int[] anchos) {
        for (int c = 0; c < anchos.length; c++) {
            sheet.setColumnWidth(c, anchos[c] * 256);
        }
    }

    private static final class Acumulado {
        long cantidad;
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal igv = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        void sumar(VentaSireEntity v) {
            cantidad++;
            base = base.add(nz(v.getBaseImponible()));
            igv = igv.add(nz(v.getIgv()));
            total = total.add(nz(v.getImporteTotal()));
        }

        void escribir(Row row, int col, Estilos e, boolean vacio) {
            Cell c = row.createCell(col);
            c.setCellValue(cantidad);
            c.setCellStyle(vacio ? e.enteroTenue : e.entero);
            monto(row, col + 1, base, vacio ? e.montoTenue : e.monto);
            monto(row, col + 2, igv, vacio ? e.montoTenue : e.monto);
            monto(row, col + 3, total, vacio ? e.montoTenue : e.monto);
        }

        private static BigDecimal nz(BigDecimal v) {
            return v == null ? BigDecimal.ZERO : v;
        }
    }

    private static final class Estilos {
        final CellStyle titulo, etiqueta, texto, textoTenue, cabecera, fecha, monto, montoTenue, entero, enteroTenue,
                total, totalMonto, totalEntero;

        Estilos(Workbook wb) {
            DataFormat formato = wb.createDataFormat();
            short fmtMonto = formato.getFormat("#,##0.00;-#,##0.00");
            short fmtEntero = formato.getFormat("#,##0");

            Font negrita = wb.createFont();
            negrita.setBold(true);
            Font grande = wb.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 14);
            Font gris = wb.createFont();
            gris.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            Font blancaNegrita = wb.createFont();
            blancaNegrita.setBold(true);
            blancaNegrita.setColor(IndexedColors.WHITE.getIndex());

            titulo = wb.createCellStyle();
            titulo.setFont(grande);
            etiqueta = wb.createCellStyle();
            etiqueta.setFont(negrita);
            texto = wb.createCellStyle();
            textoTenue = wb.createCellStyle();
            textoTenue.setFont(gris);

            cabecera = wb.createCellStyle();
            cabecera.setFont(blancaNegrita);
            cabecera.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera.setVerticalAlignment(VerticalAlignment.CENTER);

            fecha = wb.createCellStyle();
            fecha.setDataFormat(formato.getFormat("dd/mm/yyyy"));
            fecha.setAlignment(HorizontalAlignment.LEFT);
            monto = wb.createCellStyle();
            monto.setDataFormat(fmtMonto);
            montoTenue = wb.createCellStyle();
            montoTenue.setDataFormat(fmtMonto);
            montoTenue.setFont(gris);
            entero = wb.createCellStyle();
            entero.setDataFormat(fmtEntero);
            enteroTenue = wb.createCellStyle();
            enteroTenue.setDataFormat(fmtEntero);
            enteroTenue.setFont(gris);

            total = wb.createCellStyle();
            total.setFont(negrita);
            total.setBorderTop(BorderStyle.THIN);
            totalMonto = wb.createCellStyle();
            totalMonto.cloneStyleFrom(total);
            totalMonto.setDataFormat(fmtMonto);
            totalEntero = wb.createCellStyle();
            totalEntero.cloneStyleFrom(total);
            totalEntero.setDataFormat(fmtEntero);
        }
    }

}
