package com.zam.sunat_service.entity;

/**
 * Qué comprobantes incluye el reporte Excel de ventas. Las notas de crédito/débito (07/08) van con el tipo de
 * comprobante que modifican, que se reconoce por la serie: las de boletas empiezan con B (o EB en SEE-SOL).
 */
public enum TipoReporteVentas {

    BOLETAS("Boletas", "Boletas de venta y sus notas de crédito/débito"),
    FACTURAS("Facturas", "Facturas y sus notas de crédito/débito"),
    TODOS("Ventas", "Todos los comprobantes de venta");

    private final String nombreArchivo;
    private final String descripcion;

    TipoReporteVentas(String nombreArchivo, String descripcion) {
        this.nombreArchivo = nombreArchivo;
        this.descripcion = descripcion;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean incluye(VentaSireEntity venta) {
        if (this == TODOS) {
            return true;
        }
        String tipo = venta.getTipoComprobante();
        boolean esNota = "07".equals(tipo) || "08".equals(tipo);
        boolean serieDeBoleta = esSerieDeBoleta(venta.getSerie());
        if (this == BOLETAS) {
            return "03".equals(tipo) || (esNota && serieDeBoleta);
        }
        return "01".equals(tipo) || (esNota && !serieDeBoleta);
    }

    private static boolean esSerieDeBoleta(String serie) {
        if (serie == null) {
            return false;
        }
        String s = serie.trim().toUpperCase();
        return s.startsWith("B") || s.startsWith("EB");
    }

}
