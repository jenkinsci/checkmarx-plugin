package com.checkmarx.jenkins.cryptography;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.commons.codec.binary.Hex;

/**
 * @author tsahi
 * @since 02/02/16
 */
public class CryptographicService {
    public String calculateSHA1(File resourceFile) throws IOException {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException var12) {
            throw new IllegalStateException(var12.getMessage(), var12);
        }

        try (FileInputStream fis = new FileInputStream(resourceFile)) {
            byte[] buffer = new byte['?'];
            for (int e = fis.read(buffer, 0, '?'); e >= 0; e = fis.read(buffer, 0, '?')) {
                messageDigest.update(buffer, 0, e);
            }
        }

        return Hex.encodeHexString(messageDigest.digest());
    }
}
