package com.zam.sunat_service.response;

public record ReporteExcel(String nombreArchivo, byte[] contenido) {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

}
