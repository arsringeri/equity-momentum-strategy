package com.momentum.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads and exposes configuration from momentum.properties.
 * All keys are prefixed by section (kite, portfolio, data, log).
 */
public class MomentumConfig {

    private static final String DEFAULT_CONFIG = "momentum.properties";

    private final Properties props = new Properties();

    public MomentumConfig() {
        this(DEFAULT_CONFIG);
    }

    public MomentumConfig(String path) {
        // Try external file first, then classpath
        try (FileInputStream fis = new FileInputStream(path)) {
            props.load(fis);
            return;
        } catch (IOException ignored) {}
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is != null) props.load(is);
        } catch (IOException ignored) {}
    }

    // --- Kite ---

    public String getApiKey() {
        return props.getProperty("kite.api_key", "").trim();
    }

    public String getTokenFile() {
        return props.getProperty("kite.token_file", "C:/Input/store.txt").trim();
    }

    // --- Portfolio ---

    public double getInitialCapital() {
        return Double.parseDouble(props.getProperty("portfolio.initial_capital", "1000000.0").trim());
    }

    public int getTopN() {
        return Integer.parseInt(props.getProperty("portfolio.top_n", "20").trim());
    }

    /** Day-of-week name for rebalancing, e.g. "MONDAY". */
    public String getRebalanceDay() {
        return props.getProperty("portfolio.rebalance_day", "MONDAY").trim().toUpperCase();
    }

    // --- Data ---

    public String getUniverseFile() {
        return props.getProperty("data.universe_file", "data/nifty500.csv").trim();
    }

    public int getLookbackExtraDays() {
        return Integer.parseInt(props.getProperty("data.lookback_extra_days", "380").trim());
    }

    public long getApiCallDelayMs() {
        return Long.parseLong(props.getProperty("data.api_call_delay_ms", "100").trim());
    }

    // --- Log ---

    public String getLogLevel() {
        return props.getProperty("log.level", "INFO").trim();
    }

    /** Override any property at runtime (useful for tests). */
    public void set(String key, String value) {
        props.setProperty(key, value);
    }
}
