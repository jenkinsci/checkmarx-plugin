package com.checkmarx.jenkins;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Aes {
    private static SecretKeySpec secretKey;
    private static byte[] key;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    private static void setKey(String myKey) {
        MessageDigest sha = null;
        try {
            key = myKey.getBytes(CxConfig.getCharsetName());
            sha = MessageDigest.getInstance(CxConfig.getAlgorithm());
            key = sha.digest(key);
            key = Arrays.copyOf(key, CxConfig.getLength());
            secretKey = new SecretKeySpec(key, CxConfig.getAes());
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            System.err.println("Error while setting key: " + e.toString());
        }
    }

    public static String encrypt(String strToEncrypt, String secret) {
        try {
            setKey(secret);

            byte[] iv = new byte[GCM_IV_LENGTH];
            (new SecureRandom()).nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec ivSpec = new GCMParameterSpec(GCM_TAG_LENGTH * Byte.SIZE, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey,ivSpec);

            byte[] cipherText = cipher.doFinal(strToEncrypt.getBytes("UTF8"));
            byte[] encrypted = new byte[iv.length + cipherText.length];

            System.arraycopy(iv, 0, encrypted, 0, iv.length);
            System.arraycopy(cipherText, 0, encrypted, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            System.err.println("Error while encrypting: " + e.toString());
        }
        return null;
    }

    public static String decrypt(String strToDecrypt, String secret) {
        try {
            setKey(secret);

            byte[] decoded = Base64.getDecoder().decode(strToDecrypt);

            byte[] iv = Arrays.copyOfRange(decoded, 0, GCM_IV_LENGTH);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPADDING");
            GCMParameterSpec ivSpec = new GCMParameterSpec(GCM_TAG_LENGTH * Byte.SIZE, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey,ivSpec);

            byte[] cipherText = cipher.doFinal(decoded, GCM_IV_LENGTH, decoded.length - GCM_IV_LENGTH);

            return new String(cipherText, "UTF8");
        } catch (Exception e) {
            System.err.println("Error while decrypting: " + e.toString());
        }
        return null;
    }
}
