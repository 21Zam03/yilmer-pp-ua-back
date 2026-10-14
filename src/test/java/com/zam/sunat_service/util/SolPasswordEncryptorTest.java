package com.zam.sunat_service.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SolPasswordEncryptorTest {

    private final SolPasswordEncryptor encryptor =
            new SolPasswordEncryptor(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void decryptReturnsOriginalPassword() {
        String encrypted = encryptor.encrypt("Clave123");

        assertNotEquals("Clave123", encrypted);
        assertEquals("Clave123", encryptor.decrypt(encrypted));
    }

    @Test
    void sameInputProducesDifferentCipherTexts() {
        // IV aleatorio por cifrado: dos clientes con la misma clave no se delatan en la BD.
        assertNotEquals(encryptor.encrypt("Clave123"), encryptor.encrypt("Clave123"));
    }

    @Test
    void tamperedCipherTextIsRejected() {
        byte[] bytes = Base64.getDecoder().decode(encryptor.encrypt("Clave123"));
        bytes[bytes.length - 1] ^= 1;

        assertThrows(IllegalStateException.class,
                () -> encryptor.decrypt(Base64.getEncoder().encodeToString(bytes)));
    }

    @Test
    void rejectsKeyThatIsNotAes256() {
        assertThrows(IllegalStateException.class,
                () -> new SolPasswordEncryptor(Base64.getEncoder().encodeToString(new byte[16])));
    }

}
