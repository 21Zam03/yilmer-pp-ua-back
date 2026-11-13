package com.zam.sunat_service.service.impl;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.entity.TipoReporteVentas;
import com.zam.sunat_service.entity.VentaSireEntity;
import com.zam.sunat_service.exceptions.BusinessException;
import com.zam.sunat_service.repository.ClientRepository;
import com.zam.sunat_service.repository.VentaSireRepository;
import com.zam.sunat_service.response.ReporteExcel;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReporteVentasServiceImplTest {

    private static final String RUC = "10733495273";
    private static final String PERIODO = "202609";
    // Encabezado: 5 filas + 1 en blanco; los títulos van en la fila 6 (índice base 0).
    private static final int FILA_TITULOS = 6;

    private VentaSireRepository ventaRepository;
    private ReporteVentasServiceImpl service;
    private ClientEntity client;

    @BeforeEach
    void setUp() {
        ClientRepository clientRepository = mock(ClientRepository.class);
        ventaRepository = mock(VentaSireRepository.class);
        ZoneId lima = ZoneId.of("America/Lima");
        Clock clock = Clock.fixed(ZonedDateTime.of(2026, 11, 5, 12, 0, 0, 0, lima).toInstant(), lima);
        service = new ReporteVentasServiceImpl(clientRepository, ventaRepository, clock);

        client = new ClientEntity();
        client.setRuc(RUC);
        client.setBusinessName("PARDO OLAYA MARLENY");
        when(clientRepository.findByRuc(RUC)).thenReturn(Optional.of(client));

        List<VentaSireEntity> ventas = new ArrayList<>();
        ventas.add(venta("2026-09-30", "03", "EB01", "10", "1", "12345678", "JUAN PEREZ", "100.00", "18.00", "118.00"));
        ventas.add(venta("2026-09-30", "03", "EB01", "9", null, null, null, "16.95", "3.05", "20.00"));
        ventas.add(venta("2026-09-02", "03", "B001", "1", "1", "87654321", "ANA RUIZ", "50.00", "9.00", "59.00"));
        ventas.add(venta("2026-09-15", "07", "BC01", "1", "1", "12345678", "JUAN PEREZ", "-10.00", "-1.80", "-11.80"));
        ventas.add(venta("2026-09-10", "01", "F001", "5", "6", "20100070970", "SUPERMERCADOS", "1000.00", "180.00", "1180.00"));
        ventas.add(venta("2026-09-11", "07", "FC01", "2", "6", "20100070970", "SUPERMERCADOS", "-100.00", "-18.00", "-118.00"));
        when(ventaRepository.findByClientAndPeriodo(client, PERIODO)).thenReturn(ventas);
    }

    @Test
    void boletasIncluyeSusNotasYExcluyeFacturas() throws Exception {
        ReporteExcel reporte = service.exportarVentas(RUC, PERIODO, TipoReporteVentas.BOLETAS);

        assertThat(reporte.nombreArchivo()).isEqualTo("Boletas_10733495273_202609.xlsx");
        try (Workbook wb = abrir(reporte)) {
            Sheet hoja = wb.getSheet("Comprobantes");
            assertThat(texto(hoja, 0, 0)).isEqualTo("PARDO OLAYA MARLENY");
            assertThat(texto(hoja, 2, 1)).isEqualTo("Setiembre 2026");
            assertThat(texto(hoja, FILA_TITULOS, 0)).isEqualTo("Fecha emisión");

            // 4 filas: 3 boletas + la nota de crédito de serie BC01, ordenadas por fecha y número numérico.
            assertThat(series(hoja)).containsExactly("B001-1", "BC01-1", "EB01-9", "EB01-10");
            Row primera = hoja.getRow(FILA_TITULOS + 1);
            assertThat(primera.getCell(0).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 2));
            assertThat(primera.getCell(4).getStringCellValue()).isEqualTo("DNI");
            assertThat(hoja.getRow(FILA_TITULOS + 2).getCell(1).getStringCellValue()).isEqualTo("Nota de crédito");
            assertThat(hoja.getRow(FILA_TITULOS + 3).getCell(6).getStringCellValue()).isEqualTo("Sin nombre");

            Row total = hoja.getRow(FILA_TITULOS + 5);
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("Total (4 comprobantes)");
            assertThat(total.getCell(9).getCellFormula()).isEqualTo("SUM(J8:J11)");
            assertThat(evaluar(wb, total.getCell(9))).isEqualTo(185.20); // 59 - 11.80 + 20 + 118
        }
    }

    @Test
    void facturasIncluyeSusNotas() throws Exception {
        try (Workbook wb = abrir(service.exportarVentas(RUC, PERIODO, TipoReporteVentas.FACTURAS))) {
            assertThat(series(wb.getSheet("Comprobantes"))).containsExactly("F001-5", "FC01-2");
        }
    }

    @Test
    void resumenPorDiaTieneTodosLosDiasDelMes() throws Exception {
        try (Workbook wb = abrir(service.exportarVentas(RUC, PERIODO, TipoReporteVentas.BOLETAS))) {
            Sheet resumen = wb.getSheet("Resumen por día");
            // 30 días + fila de total
            assertThat(resumen.getLastRowNum()).isEqualTo(FILA_TITULOS + 30 + 1);
            Row dia30 = resumen.getRow(FILA_TITULOS + 30);
            assertThat(dia30.getCell(1).getStringCellValue()).isEqualTo("Miércoles");
            assertThat(dia30.getCell(2).getNumericCellValue()).isEqualTo(2);
            assertThat(dia30.getCell(5).getNumericCellValue()).isEqualTo(138.00);
            Row dia1 = resumen.getRow(FILA_TITULOS + 1);
            assertThat(dia1.getCell(2).getNumericCellValue()).isZero();
            assertThat(evaluar(wb, resumen.getRow(FILA_TITULOS + 31).getCell(2))).isEqualTo(4);
        }
    }

    @Test
    void sinComprobantesDelTipoLanzaError() {
        when(ventaRepository.findByClientAndPeriodo(client, PERIODO)).thenReturn(List.of(
                venta("2026-09-10", "01", "F001", "5", "6", "20100070970", "X", "1", "0.18", "1.18")));

        assertThatThrownBy(() -> service.exportarVentas(RUC, PERIODO, TipoReporteVentas.BOLETAS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No hay boletas sincronizadas de Setiembre 2026");
    }

    @Test
    void periodoInvalido() {
        assertThatThrownBy(() -> service.exportarVentas(RUC, "2026-09", TipoReporteVentas.TODOS))
                .isInstanceOf(BusinessException.class);
    }

    private static Workbook abrir(ReporteExcel reporte) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(reporte.contenido()));
    }

    private static String texto(Sheet hoja, int fila, int col) {
        return hoja.getRow(fila).getCell(col).getStringCellValue();
    }

    private static List<String> series(Sheet hoja) {
        List<String> series = new ArrayList<>();
        for (int r = FILA_TITULOS + 1; r < hoja.getLastRowNum(); r++) {
            Row row = hoja.getRow(r);
            series.add(row.getCell(2).getStringCellValue() + "-" + row.getCell(3).getStringCellValue());
        }
        return series;
    }

    private static double evaluar(Workbook wb, Cell cell) {
        return Math.round(wb.getCreationHelper().createFormulaEvaluator().evaluate(cell).getNumberValue() * 100) / 100.0;
    }

    private VentaSireEntity venta(String fecha, String tipo, String serie, String numero, String tipoDoc, String nroDoc,
                                  String nombre, String base, String igv, String total) {
        VentaSireEntity v = new VentaSireEntity();
        v.setClient(client);
        v.setPeriodo(PERIODO);
        v.setFechaEmision(LocalDate.parse(fecha));
        v.setTipoComprobante(tipo);
        v.setSerie(serie);
        v.setNumero(numero);
        v.setClienteTipoDoc(tipoDoc);
        v.setClienteNroDoc(nroDoc);
        v.setClienteNombre(nombre);
        v.setBaseImponible(new BigDecimal(base));
        v.setIgv(new BigDecimal(igv));
        v.setImporteTotal(new BigDecimal(total));
        return v;
    }

}
