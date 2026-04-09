package com.momentum.tools;

import com.momentum.broker.KiteSessionManager;
import com.momentum.data.UniverseLoader;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.Instrument;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.*;

/**
 * One-time utility: connects to Kite, fetches all NSE instruments,
 * matches against nifty500.csv symbols, and writes nifty500_with_tokens.csv.
 *
 * Run from Eclipse:
 *   Right-click TokenExporter.java → Run As → Java Application
 *
 * Output: nifty500_with_tokens.csv in the project root
 *   Columns: symbol, instrument_token, exchange_token, name, last_price,
 *            expiry, strike, tick_size, lot_size, instrument_type, segment, exchange
 *
 * Use this file to:
 *   - Verify which Nifty 500 symbols were NOT matched (listed in console)
 *   - Debug token lookup issues in KitePriceDataProvider
 *   - Keep a snapshot of tokens (note: tokens can change after corporate actions)
 */
public class TokenExporter {

    private static final Logger LOG = Logger.getLogger(TokenExporter.class.getName());

    private static final String UNIVERSE_FILE = "data/nifty500.csv";
    private static final String OUTPUT_FILE   = "nifty500_with_tokens.csv";

    public static void main(String[] args) throws Exception {
        configureLogging();

        LOG.info("=== TokenExporter ===");
        LOG.info("Universe : " + UNIVERSE_FILE);
        LOG.info("Output   : " + OUTPUT_FILE);

        // Load universe symbols
        List<String> universe = UniverseLoader.load(UNIVERSE_FILE);
        Set<String> universeSet = new LinkedHashSet<>();
        for (String s : universe) universeSet.add(s.trim().toUpperCase());
        LOG.info("Universe symbols: " + universeSet.size());

        // Connect to Kite
        LOG.info("Connecting to Kite (will prompt for store.enc password)...");
        KiteConnect kite = KiteSessionManager.getConnection();

        // Fetch all NSE instruments
        LOG.info("Fetching NSE instruments from Kite...");
        long t0 = System.currentTimeMillis();
        List<Instrument> allInstruments;
        try {
            allInstruments = kite.getInstruments("NSE");
        } catch (com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException ke) {
            throw new RuntimeException("Kite API error fetching instruments: " + ke.getMessage(), ke);
        }
        LOG.info("Fetched " + allInstruments.size() + " NSE instruments in "
                + (System.currentTimeMillis() - t0) + "ms");

        // Build map: symbol → instrument
        Map<String, Instrument> symbolMap = new LinkedHashMap<>();
        for (Instrument inst : allInstruments) {
            if (inst.tradingsymbol != null) {
                symbolMap.put(inst.tradingsymbol.trim().toUpperCase(), inst);
            }
        }

        // Match and export
        List<String> matched   = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();

        try (PrintWriter pw = new PrintWriter(new FileWriter(OUTPUT_FILE))) {
            pw.println("symbol,instrument_token,exchange_token,name,last_price,"
                    + "tick_size,lot_size,instrument_type,segment,exchange");

            for (String symbol : universeSet) {
                Instrument inst = symbolMap.get(symbol);
                if (inst == null) {
                    unmatched.add(symbol);
                    // Write a row with empty token so the symbol is visible
                    pw.printf("%s,,,,,,,,,%n", symbol);
                    continue;
                }
                matched.add(symbol);
                pw.printf("%s,%d,%d,%s,%.2f,%.2f,%d,%s,%s,%s%n",
                        symbol,
                        inst.instrument_token,
                        inst.exchange_token,
                        csvEscape(inst.name),
                        inst.last_price,
                        inst.tick_size,
                        inst.lot_size,
                        nvl(inst.instrument_type),
                        nvl(inst.segment),
                        nvl(inst.exchange));
            }
        }

        // Report
        LOG.info("=== Export Complete ===");
        LOG.info("Matched   : " + matched.size() + "/" + universeSet.size());
        LOG.info("Unmatched : " + unmatched.size());
        LOG.info("Output    : " + new java.io.File(OUTPUT_FILE).getAbsolutePath());

        if (!unmatched.isEmpty()) {
            LOG.warning("--- Symbols NOT found in Kite NSE instrument list ---");
            for (String sym : unmatched) {
                LOG.warning("  UNMATCHED: " + sym);
            }
            LOG.warning("These symbols may be delisted, renamed, or on a different exchange.");
            LOG.warning("Remove them from nifty500.csv or replace with the current NSE tradingsymbol.");
        }

        System.out.println("\nDone! Written to: " + OUTPUT_FILE);
        System.out.println("Matched:   " + matched.size());
        System.out.println("Unmatched: " + unmatched.size());
        if (!unmatched.isEmpty()) {
            System.out.println("Unmatched symbols: " + unmatched);
        }
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String nvl(String s) {
        return (s == null) ? "" : s.trim();
    }

    private static void configureLogging() {
        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) root.removeHandler(h);
        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.ALL);
        ch.setFormatter(new SimpleFormatter() {
            @Override public String format(LogRecord r) {
                return "[" + new SimpleDateFormat("HH:mm:ss").format(new Date(r.getMillis()))
                        + "] " + r.getLevel().getName() + "  " + r.getMessage() + "\n";
            }
        });
        root.addHandler(ch);
        root.setLevel(Level.INFO);
    }
}
