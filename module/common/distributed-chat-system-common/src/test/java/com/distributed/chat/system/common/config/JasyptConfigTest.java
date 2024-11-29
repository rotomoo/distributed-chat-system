package com.distributed.chat.system.common.config;

import org.jasypt.encryption.pbe.PBEStringCleanablePasswordEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("jasypt 테스트")
class JasyptConfigTest {

    @Test
    @DisplayName("true case: jasypt 복호화 테스트")
    void decryptTest() {
        // arrange
        String encryptorTestKey = "encryptorKey";
        PBEStringCleanablePasswordEncryptor encryptor = initEncryptor(encryptorTestKey);

        String encryptTarget = "encryptTest";
        String expect = "1fRI5St+w6+mA2SoPyBujpnivrTVFdpV";

        // act
        String decrypt = encryptor.decrypt(expect);

        // assert
        assertEquals(decrypt, encryptTarget);
    }

    @Test
    @DisplayName("true case: jasypt 정상 암/복호화")
    void encryptDecryptTest() {
        // arrange
        String encryptorTestKey = "encryptorKey";
        PBEStringCleanablePasswordEncryptor encryptor = initEncryptor(encryptorTestKey);

        String encryptTarget = "encryptTest";

        // act
        String encrypt = encryptor.encrypt(encryptTarget);
        String decrypt = encryptor.decrypt(encrypt);

        // assert
        assertEquals(encryptTarget, decrypt);
    }

    private PBEStringCleanablePasswordEncryptor initEncryptor(String encryptorKey) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(encryptorKey);
        encryptor.setAlgorithm("PBEWithMD5AndDES");

        return encryptor;
    }
}