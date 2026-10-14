package com.zam.sunat_service.integration;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.entity.VentaSireEntity;
import com.zam.sunat_service.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SireVentasTxtParserTest {

    public static final String CABECERA = "Ruc|Razon Social|Periodo|CAR SUNAT|Fecha de emisión|Fecha Vcto/Pago|Tipo CP/Doc.|Serie del CDP"
            + "|Nro CP o Doc. Nro Inicial (Rango)|Nro Final (Rango)|Tipo Doc Identidad|Nro Doc Identidad"
            + "|Apellidos Nombres/ Razón Social|Valor Facturado Exportación|BI Gravada|Dscto BI|IGV / IPM|Dscto IGV / IPM"
            + "|Mto Exonerado|Mto Inafecto|ISC|BI Grav IVAP|IVAP|ICBPER|Otros Tributos|Total CP|Moneda|Tipo Cambio";

    private final SireVentasTxtParser parser = new SireVentasTxtParser();
    private final ClientEntity client = new ClientEntity();

    public static String linea(String car, String fecha, String tipo, String serie, String numero, String tipoDoc,
                        String nroDoc, String nombre, String base, String igv, String total) {
        return String.join("|", "10733495273", "PEREZ JUAN", "202610", car, fecha, "", tipo, serie, numero, "",
                tipoDoc, nroDoc, nombre, "0.00", base, "0.00", igv, "0.00", "0.00", "0.00", "0.00", "0.00", "0.00",
                "0.00", "0.00", total, "PEN", "1.000");
    }

    public static byte[] zip(String nombre, String contenido, Charset charset) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry(nombre));
            zos.write(contenido.getBytes(charset));
            zos.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void mapeaColumnasEsencialesYSaltaCabecera() throws IOException {
        String txt = String.join("\r\n", CABECERA,
                linea("1073349527301F00100000123", "05/10/2026", "01", "F001", "123", "6", "20100070970",
                        "SUPERMERCADOS PERUANOS S.A.", "1000.00", "180.00", "1180.00"),
                linea("1073349527303B00100000045", "06/10/2026", "03", "B001", "45", "-", "-",
                        "-", "50.00", "", "59.00"),
                "");

        List<VentaSireEntity> ventas = parser.parsear(zip("LE1073349527320261000014040001EXP2.txt", txt, StandardCharsets.UTF_8), client, "202610");

        assertThat(ventas).hasSize(2);
        VentaSireEntity factura = ventas.get(0);
        assertThat(factura.getCarSunat()).isEqualTo("1073349527301F00100000123");
        assertThat(factura.getFechaEmision()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(factura.getTipoComprobante()).isEqualTo("01");
        assertThat(factura.getSerie()).isEqualTo("F001");
        assertThat(factura.getNumero()).isEqualTo("123");
        assertThat(factura.getClienteTipoDoc()).isEqualTo("6");
        assertThat(factura.getClienteNroDoc()).isEqualTo("20100070970");
        assertThat(factura.getClienteNombre()).isEqualTo("SUPERMERCADOS PERUANOS S.A.");
        assertThat(factura.getBaseImponible()).isEqualByComparingTo("1000.00");
        assertThat(factura.getIgv()).isEqualByComparingTo("180.00");
        assertThat(factura.getImporteTotal()).isEqualByComparingTo("1180.00");
        assertThat(factura.getClient()).isSameAs(client);
        assertThat(factura.getPeriodo()).isEqualTo("202610");

        // Guiones y vacíos: textos a null, montos a 0.
        VentaSireEntity boleta = ventas.get(1);
        assertThat(boleta.getClienteTipoDoc()).isNull();
        assertThat(boleta.getClienteNroDoc()).isNull();
        assertThat(boleta.getClienteNombre()).isNull();
        assertThat(boleta.getIgv()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void leeArchivoEnIso88591() throws IOException {
        String txt = CABECERA + "\n" + linea("CAR1", "05/10/2026", "01", "F001", "1", "6", "20100070970",
                "COMPAÑÍA ÑANDÚ S.A.C.", "10", "1.80", "11.80");

        List<VentaSireEntity> ventas = parser.parsear(zip("rvie.txt", txt, StandardCharsets.ISO_8859_1), client, "202610");

        assertThat(ventas.get(0).getClienteNombre()).isEqualTo("COMPAÑÍA ÑANDÚ S.A.C.");
    }

    @Test
    void descartaLineasSinCarOIncompletas() throws IOException {
        String txt = String.join("\n", CABECERA,
                linea("-", "05/10/2026", "01", "F001", "1", "6", "1", "X", "1", "1", "2"),
                "10733495273|INCOMPLETA|202610",
                linea("CAR2", "05/10/2026", "01", "F001", "2", "6", "1", "X", "1", "1", "2"));

        assertThat(parser.parsear(zip("rvie.txt", txt, StandardCharsets.UTF_8), client, "202610"))
                .extracting(VentaSireEntity::getCarSunat)
                .containsExactly("CAR2");
    }

    private static final String TXT_UNA_VENTA = CABECERA + "\n"
            + linea("CAR1", "05/10/2026", "01", "F001", "1", "6", "20100070970", "A", "100", "18", "118");

    @Test
    void aceptaZipEnBase64() throws IOException {
        byte[] base64 = java.util.Base64.getMimeEncoder()
                .encode(zip("rvie.txt", TXT_UNA_VENTA, StandardCharsets.UTF_8));

        assertThat(parser.parsear(base64, client, "202610")).extracting(VentaSireEntity::getCarSunat).containsExactly("CAR1");
    }

    @Test
    void aceptaZipConMarcadorDeSpanningAlInicio() throws IOException {
        // Así llega de SUNAT: PK 07 08 + el ZIP normal.
        byte[] normal = zip("LE107334952732026100014040001EXP2.txt", TXT_UNA_VENTA, StandardCharsets.UTF_8);
        byte[] conMarcador = new byte[normal.length + 4];
        conMarcador[0] = 0x50;
        conMarcador[1] = 0x4B;
        conMarcador[2] = 0x07;
        conMarcador[3] = 0x08;
        System.arraycopy(normal, 0, conMarcador, 4, normal.length);

        assertThat(parser.parsear(conMarcador, client, "202610")).extracting(VentaSireEntity::getCarSunat).containsExactly("CAR1");
    }

    @Test
    void aceptaTxtSinComprimir() {
        assertThat(parser.parsear(TXT_UNA_VENTA.getBytes(StandardCharsets.UTF_8), client, "202610")).hasSize(1);
    }

    @Test
    void aceptaGzip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(out)) {
            gzip.write(zip("rvie.txt", TXT_UNA_VENTA, StandardCharsets.UTF_8));
        }

        assertThat(parser.parsear(out.toByteArray(), client, "202610")).hasSize(1);
    }

    @Test
    void jsonEnLugarDelArchivoMuestraElContenido() {
        byte[] json = "{\"cod\":\"500\",\"msg\":\"Archivo no disponible\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parsear(json, client, "202610"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JSON")
                .hasMessageContaining("Archivo no disponible");
    }

    @Test
    void zipSinTxtLanzaError() throws IOException {
        assertThatThrownBy(() -> parser.parsear(zip("reporte.xlsx", "x", StandardCharsets.UTF_8), client, "202610"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(".txt");
        assertThatThrownBy(() -> parser.parsear("no es zip".getBytes(), client, "202610"))
                .isInstanceOf(BusinessException.class);
    }

}
