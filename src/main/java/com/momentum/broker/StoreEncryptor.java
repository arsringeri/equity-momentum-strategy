package com.momentum.broker;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

/**
 * AES-256-GCM encryption/decryption for c:\Input\store.enc
 *
 * Identical algorithm to com.tradingbot.broker.StoreEncryptor in C:/bot —
 * the same store.enc file is shared between both projects.
 *
 * File layout: [16-byte PBKDF2 salt] [12-byte AES-GCM IV] [ciphertext + 16-byte GCM tag]
 * Key derivation: PBKDF2WithHmacSHA256, 65 536 iterations, 256-bit key
 */
public class StoreEncryptor {

    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int KEY_BITS          = 256;
    static final int         SALT_BYTES        = 16;
    static final int         IV_BYTES          = 12;
    private static final int GCM_TAG_BITS      = 128;

    /**
     * Decrypt bytes produced by the C:/bot StoreEncryptor.
     * Throws {@link javax.crypto.AEADBadTagException} if the password is wrong.
     */
    public static byte[] decrypt(byte[] data, char[] password) throws Exception {
        int minLen = SALT_BYTES + IV_BYTES + GCM_TAG_BITS / 8;
        if (data.length < minLen) {
            throw new IllegalArgumentException(
                    "store.enc is too short (" + data.length + " bytes) — file may be corrupt");
        }

        byte[] salt = Arrays.copyOfRange(data, 0,          SALT_BYTES);
        byte[] iv   = Arrays.copyOfRange(data, SALT_BYTES, SALT_BYTES + IV_BYTES);
        byte[] ct   = Arrays.copyOfRange(data, SALT_BYTES + IV_BYTES, data.length);

        SecretKey key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ct);   // throws AEADBadTagException if wrong password
    }

    // -------------------------------------------------------------------------

    private static SecretKey deriveKey(char[] password, byte[] salt)
            throws InvalidKeySpecException, Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    static byte[] readFile(File f) throws IOException {
        byte[] buf = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int read = 0;
            while (read < buf.length) {
                int n = fis.read(buf, read, buf.length - read);
                if (n < 0) break;
                read += n;
            }
        }
        return buf;
    }
}
