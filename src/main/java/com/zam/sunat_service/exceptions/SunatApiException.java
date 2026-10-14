package com.zam.sunat_service.exceptions;

/**
 * Fallo al comunicarse con las APIs de SUNAT (credenciales rechazadas, SUNAT caída, timeout...).
 */
public class SunatApiException extends RuntimeException {

    public SunatApiException(String message) {
        super(message);
    }

    public SunatApiException(String message, Throwable cause) {
        super(message, cause);
    }

}
