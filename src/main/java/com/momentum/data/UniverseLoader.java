package com.momentum.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Reads universe CSV (symbol,name) and returns the list of NSE symbols.
 * Skips header, blank lines, and comment lines (#).
 */
public class UniverseLoader {

    private static final Logger LOG = Logger.getLogger(UniverseLoader.class.getName());

    public static List<String> load(String filePath) throws Exception {
        List<String> symbols    = new ArrayList<>();
        int skippedBlank        = 0;
        int skippedComment      = 0;
        boolean loadedFromFile  = true;

        LOG.info("Loading universe from: " + filePath);

        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            LOG.fine("Universe file found on filesystem: " + filePath);
        } catch (Exception e) {
            LOG.warning("Universe file not found on filesystem, trying classpath: " + filePath);
            loadedFromFile = false;
            InputStream is = UniverseLoader.class.getClassLoader().getResourceAsStream(filePath);
            if (is == null) {
                LOG.severe("Universe file not found anywhere: " + filePath);
                throw new Exception("Universe file not found: " + filePath);
            }
            reader = new BufferedReader(new InputStreamReader(is));
        }

        try (BufferedReader br = reader) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) { skippedBlank++; continue; }
                if (trimmed.startsWith("#")) { skippedComment++; continue; }
                if (firstLine && trimmed.toLowerCase().startsWith("symbol")) {
                    LOG.fine("Skipping header row: " + trimmed);
                    firstLine = false;
                    continue;
                }
                firstLine = false;
                String[] parts = trimmed.split(",", 2);
                if (parts.length >= 1) {
                    String sym = parts[0].trim();
                    if (!sym.isEmpty()) symbols.add(sym);
                }
            }
        }

        LOG.info("Universe loaded: " + symbols.size() + " symbols"
                + "  (source=" + (loadedFromFile ? "file" : "classpath")
                + ", skippedBlank=" + skippedBlank
                + ", skippedComment=" + skippedComment + ")");

        if (symbols.isEmpty()) {
            LOG.warning("Universe is EMPTY — no symbols were loaded from " + filePath);
        }

        return symbols;
    }
}
