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
 *   1. API key + user ID from secrets.properties
 *   2. access_token + public_token decrypted from C:\Input\store.enc (AES-256-GCM)
 *   3. Swing password dialog on first use (up to 3 attempts)
 *
 * Same store.enc shared with C:/bot — no separate login required.
 */
public class KiteSessionManager {

    private static final Logger LOG = Logger.getLogger(KiteSessionManager.class.getName());

    private static final String SECRETS_FILE = "secrets.properties";
    private static final String TOKEN_FILE   = "C:\\Input\\store.enc";

    private static final String API_KEY;
    private static final String USER_ID;

    static {
        LOG.info("Loading secrets from: " + new File(SECRETS_FILE).getAbsolutePath());
        Properties secrets = new Properties();
        try (FileInputStream fis = new FileInputStream(SECRETS_FILE)) {
            secrets.load(fis);
            LOG.info("secrets.properties loaded OK");
        } catch (IOException e) {
            LOG.severe("secrets.properties not found: " + e.getMessage());
            throw new ExceptionInInitializerError(
                "secrets.properties not found in working directory.\n"
                + "Copy secrets.properties.example to secrets.properties and fill in your credentials.");
        }
        API_KEY = require(secrets, "kite.api_key");
        USER_ID = require(secrets, "kite.user_id");
        LOG.info("Credentials loaded — user_id=" + USER_ID
                + "  api_key=" + mask(API_KEY));
    }

    private static String require(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty() || v.contains("YOUR_")) {
            LOG.severe("secrets.properties missing or placeholder for key: " + key);
            throw new ExceptionInInitializerError(
                "secrets.properties is missing or has a placeholder value for: " + key);
        }
        return v.trim();
    }

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static KiteConnect instance = null;

    /**
     * Returns the authenticated KiteConnect singleton.
     * First call prompts for store.enc decryption password via Swing dialog.
     */
    public static synchronized KiteConnect getConnection() {
        if (instance != null) {
            LOG.fine("Returning cached KiteConnect instance");
            return instance;
        }

        LOG.info("Initialising KiteConnect — user_id=" + USER_ID);
        instance = new KiteConnect(API_KEY);
        instance.setUserId(USER_ID);

        File tokenFile = new File(TOKEN_FILE);
        LOG.info("Token file: " + tokenFile.getAbsolutePath()
                + "  exists=" + tokenFile.exists()
                + "  size=" + (tokenFile.exists() ? tokenFile.length() + " bytes" : "N/A"));

        if (!tokenFile.exists()) {
            instance = null;
            LOG.severe("store.enc not found at: " + tokenFile.getAbsolutePath());
            throw new RuntimeException(
                "Encrypted token file not found: " + tokenFile.getAbsolutePath()
                + "\nRun update-tokens.bat from C:/bot, or run StoreEncryptor to create it.");
        }

        Properties tokens = null;
        String errorMsg   = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            LOG.info("Password prompt — attempt " + attempt + "/3");
            char[] password = promptPassword(errorMsg);

            if (password == null) {
                instance = null;
                LOG.warning("User cancelled the store.enc password dialog");
                throw new RuntimeException("Login cancelled by user.");
            }

            try {
                tokens = decryptTokenFile(tokenFile, password);
                LOG.info("store.enc decrypted successfully on attempt " + attempt);
                break;
            } catch (RuntimeException ex) {
                if (ex.getCause() instanceof javax.crypto.AEADBadTagException) {
                    LOG.warning("Wrong password on attempt " + attempt + "/3");
                    errorMsg = "Wrong password. Please try again.";
                    if (attempt == 3) {
                        instance = null;
                        LOG.severe("All 3 password attempts failed — aborting");
                        throw ex;
                    }
                } else {
                    instance = null;
                    LOG.severe("Unexpected error decrypting store.enc: " + ex.getMessage());
                    throw ex;
                }
            } finally {
                Arrays.fill(password, '\0');
            }
        }

        String accessToken = tokens.getProperty("access_token", "").trim();
        String publicToken  = tokens.getProperty("public_token",  "").trim();

        LOG.info("access_token present=" + !accessToken.isEmpty()
                + "  length=" + accessToken.length());
        LOG.info("public_token  present=" + !publicToken.isEmpty()
                + "  length=" + publicToken.length());

        if (accessToken.isEmpty()) {
            instance = null;
            LOG.severe("access_token is missing or empty in decrypted store.enc");
            throw new RuntimeException("access_token missing in store.enc");
        }
        if (publicToken.isEmpty()) {
            instance = null;
            LOG.severe("public_token is missing or empty in decrypted store.enc");
            throw new RuntimeException("public_token missing in store.enc");
        }

        instance.setAccessToken(accessToken);
        instance.setPublicToken(publicToken);

        LOG.info("KiteConnect ready — authenticated as " + USER_ID);
        return instance;
    }

    /** Clears the singleton — forces re-authentication on next getConnection(). */
    public static synchronized void reset() {
        LOG.info("KiteSessionManager.reset() called — session will be re-authenticated on next use");
        instance = null;
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
            LOG.fine("Reading token file: " + tokenFile.length() + " bytes");
            byte[] encData   = StoreEncryptor.readFile(tokenFile);
            byte[] plaintext = StoreEncryptor.decrypt(encData, password);

            Properties p = new Properties();
            p.load(new ByteArrayInputStream(plaintext));
            Arrays.fill(plaintext, (byte) 0);   // wipe from memory immediately
            LOG.fine("Token properties loaded — keys: " + p.stringPropertyNames());
            return p;
        } catch (javax.crypto.AEADBadTagException e) {
            throw new RuntimeException("Wrong password for store.enc", e);
        } catch (Exception e) {
            LOG.severe("Failed to decrypt store.enc: " + e.getMessage());
            throw new RuntimeException("Failed to decrypt store.enc: " + e.getMessage(), e);
        }
    }

    /** Masks an API key for safe logging: shows first 4 + last 4 chars. */
    private static String mask(String s) {
        if (s == null || s.length() < 10) return "****";
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }
}
