package com.momentum.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Loads and exposes configuration from momentum.properties.
 */
public class MomentumConfig {

    private static final Logger LOG = Logger.getLogger(MomentumConfig.class.getName());
    private static final String DEFAULT_CONFIG = "momentum.properties";

    private final Properties props = new Properties();
    private String loadedFrom = "(none)";

    public MomentumConfig() {
        this(DEFAULT_CONFIG);
    }

    public MomentumConfig(String path) {
        // Try external file first
        try (FileInputStream fis = new FileInputStream(path)) {
            props.load(fis);
            loadedFrom = "file:" + path;
            LOG.info("Config loaded from file: " + path);
            return;
        } catch (IOException ignored) {}

        // Fall back to classpath
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                props.load(is);
                loadedFrom = "classpath:" + path;
                LOG.info("Config loaded from classpath: " + path);
            } else {
                LOG.warning("Config file not found at '" + path + "' — all settings will use defaults");
            }
        } catch (IOException e) {
            LOG.warning("Failed to load config from classpath: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Portfolio
    // -------------------------------------------------------------------------

    public double getInitialCapital() {
        return Double.parseDouble(props.getProperty("portfolio.initial_capital", "1000000.0").trim());
    }

    public int getTopN() {
        return Integer.parseInt(props.getProperty("portfolio.top_n", "20").trim());
    }

    public String getRebalanceDay() {
        return props.getProperty("portfolio.rebalance_day", "MONDAY").trim().toUpperCase();
    }

    // -------------------------------------------------------------------------
    // Data
    // -------------------------------------------------------------------------

    public String getUniverseFile() {
        return props.getProperty("data.universe_file", "data/nifty500.csv").trim();
    }

    public int getLookbackExtraDays() {
        return Integer.parseInt(props.getProperty("data.lookback_extra_days", "380").trim());
    }

    public long getApiCallDelayMs() {
        return Long.parseLong(props.getProperty("data.api_call_delay_ms", "100").trim());
    }

    // -------------------------------------------------------------------------
    // Log
    // -------------------------------------------------------------------------

    public String getLogLevel() {
        return props.getProperty("log.level", "INFO").trim();
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /** Override any property at runtime (useful for tests). */
    public void set(String key, String value) {
        props.setProperty(key, value);
    }

    /** Logs all resolved configuration values at INFO level. */
    public void logAll() {
        LOG.info("--- Resolved Configuration (source: " + loadedFrom + ") ---");
        LOG.info("  portfolio.initial_capital  = " + getInitialCapital());
        LOG.info("  portfolio.top_n            = " + getTopN());
        LOG.info("  portfolio.rebalance_day    = " + getRebalanceDay());
        LOG.info("  data.universe_file         = " + getUniverseFile());
        LOG.info("  data.lookback_extra_days   = " + getLookbackExtraDays());
        LOG.info("  data.api_call_delay_ms     = " + getApiCallDelayMs());
        LOG.info("  log.level                  = " + getLogLevel());
        LOG.info("------------------------------------------------------");
    }
}
