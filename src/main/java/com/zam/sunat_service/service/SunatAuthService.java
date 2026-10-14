package com.zam.sunat_service.service;

public interface SunatAuthService {

    /**
     * Devuelve un access token OAuth2 de SUNAT vigente para el cliente con ese RUC.
     * Reutiliza el token guardado en BD mientras no expire; si no, pide uno nuevo a SUNAT.
     */
    String obtenerTokenValido(String ruc);

    /**
     * Borra el token en caché (ej. SUNAT respondió 401 antes de la fecha de expiración guardada),
     * para que la siguiente llamada a obtenerTokenValido pida uno nuevo.
     */
    void invalidarToken(String ruc);

}
