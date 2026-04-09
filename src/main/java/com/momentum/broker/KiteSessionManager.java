package com.momentum.broker;

import com.zerodhatech.kiteconnect.KiteConnect;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * KiteSessionManager — authenticated KiteConnect singleton for the momentum project.
 *
 * Mirrors com.tradingbot.broker.KiteSession from C:/bot exactly:
 *
 *   1. API key + user ID read from secrets.properties (in the working directory)
 *   2. access_token + public_token decrypted at runtime from C:\Input\store.enc
 *      (AES-256-GCM, same encryption as C:/bot — shared file, no duplicate login)
 *   3. Password prompted via Swing dialog (up to 3 attempts)
 *
 * No credentials are ever stored in source code or momentum.properties.
 */
public class KiteSessionManager {

    private static final Logger LOG = Logger.getLogger(KiteSessionManager.class.getName());

    private static final String SECRETS_FILE = "secrets.properties";
    private static final String TOKEN_FILE   = "C:\\Input\\store.enc";

    private static final String API_KEY;
    private static final String USER_ID;

    static {
        Properties secrets = new Properties();
        try (FileInputStream fis = new FileInputStream(SECRETS_FILE)) {
            secrets.load(fis);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(
                "secrets.properties not found in working directory.\n"
                + "Copy secrets.properties.example to secrets.properties and fill in your credentials.");
        }
        API_KEY = require(secrets, "kite.api_key");
        USER_ID = require(secrets, "kite.user_id");
    }

    private static String require(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty() || v.contains("YOUR_"))
            throw new ExceptionInInitializerError(
                "secrets.properties is missing or has a placeholder value for: " + key
                + "\nEdit secrets.properties and set your real Zerodha credentials.");
        return v.trim();
    }

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static KiteConnect instance = null;

    /**
     * Returns the authenticated KiteConnect singleton.
     * On first call: prompts for the store.enc decryption password via a Swing dialog.
     * Subsequent calls return the cached instance immediately.
     */
    public static synchronized KiteConnect getConnection() {
        if (instance != null) return instance;

        instance = new KiteConnect(API_KEY);
        instance.setUserId(USER_ID);

        File tokenFile = new File(TOKEN_FILE);
        if (!tokenFile.exists()) {
            throw new RuntimeException(
                "Encrypted token file not found: " + tokenFile.getAbsolutePath()
                + "\nRun the C:/bot StoreEncryptor to create it, or run update-tokens.bat.");
        }

        Properties tokens = null;
        String errorMsg   = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            char[] password = promptPassword(errorMsg);
            if (password == null) {
                instance = null;
                throw new RuntimeException("Login cancelled by user.");
            }
            try {
                tokens = decryptTokenFile(tokenFile, password);
                break;
            } catch (RuntimeException ex) {
                if (ex.getCause() instanceof javax.crypto.AEADBadTagException) {
                    errorMsg = "Wrong password. Please try again.";
                    if (attempt == 3) { instance = null; throw ex; }
                } else {
                    instance = null; throw ex;
                }
            } finally {
                Arrays.fill(password, '\0');
            }
        }

        String accessToken = tokens.getProperty("access_token", "").trim();
        String publicToken  = tokens.getProperty("public_token", "").trim();

        if (accessToken.isEmpty())
            throw new RuntimeException("access_token missing in decrypted store.enc");
        if (publicToken.isEmpty())
            throw new RuntimeException("public_token missing in decrypted store.enc");

        instance.setAccessToken(accessToken);
        instance.setPublicToken(publicToken);

        LOG.info("KiteSessionManager: authenticated (access_token length=" + accessToken.length() + ")");
        return instance;
    }

    /** Clears the singleton — forces re-authentication on next getConnection(). */
    public static synchronized void reset() {
        instance = null;
        LOG.info("KiteSessionManager: session reset.");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static char[] promptPassword(String errorMsg) {
        JPasswordField passwordField = new JPasswordField(24);
        passwordField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill   = GridBagConstraints.HORIZONTAL;

        if (errorMsg != null) {
            JLabel err = new JLabel(errorMsg);
            err.setForeground(Color.RED);
            c.gridy = 0; panel.add(err, c);
        }
        c.gridy = (errorMsg != null) ? 1 : 0;
        panel.add(new JLabel("Store decryption password:"), c);
        c.gridy++;
        panel.add(passwordField, c);

        SwingUtilities.invokeLater(passwordField::requestFocusInWindow);

        int result = JOptionPane.showConfirmDialog(
                null, panel, "Unlock store.enc — Momentum Platform",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;
        char[] pw = passwordField.getPassword();
        passwordField.setText("");
        return pw;
    }

    private static Properties decryptTokenFile(File tokenFile, char[] password) {
        try {
            byte[] encData = StoreEncryptor.readFile(tokenFile);
            byte[] plaintext = StoreEncryptor.decrypt(encData, password);
            LOG.info("KiteSessionManager: store.enc decrypted successfully.");

            Properties p = new Properties();
            p.load(new ByteArrayInputStream(plaintext));
            Arrays.fill(plaintext, (byte) 0);   // wipe from memory
            return p;
        } catch (javax.crypto.AEADBadTagException e) {
            throw new RuntimeException("Wrong password for store.enc", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt store.enc: " + e.getMessage(), e);
        }
    }
}
