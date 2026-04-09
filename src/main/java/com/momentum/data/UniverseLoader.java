package com.momentum.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the universe CSV (symbol,name) and returns the list of NSE symbols.
 * Skips the header row and any blank / comment lines.
 */
public class UniverseLoader {

    /**
     * Loads symbols from a file path.
     * Falls back to classpath resource if the file is not found.
     */
    public static List<String> load(String filePath) throws Exception {
        List<String> symbols = new ArrayList<>();

        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filePath));
        } catch (Exception e) {
            // Try classpath
            InputStream is = UniverseLoader.class.getClassLoader().getResourceAsStream(filePath);
            if (is == null) throw new Exception("Universe file not found: " + filePath);
            reader = new BufferedReader(new InputStreamReader(is));
        }

        try (BufferedReader br = reader) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (firstLine && line.toLowerCase().startsWith("symbol")) {
                    firstLine = false;
                    continue;   // skip header
                }
                firstLine = false;
                String[] parts = line.split(",", 2);
                if (parts.length >= 1) {
                    String sym = parts[0].trim();
                    if (!sym.isEmpty()) symbols.add(sym);
                }
            }
        }

        return symbols;
    }
}
