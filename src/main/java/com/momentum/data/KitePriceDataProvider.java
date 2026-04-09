package com.momentum.data;

import com.momentum.broker.KiteSessionManager;
import com.momentum.config.MomentumConfig;
import com.momentum.model.DailyPrice;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Instrument;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * Fetches daily OHLCV data from Zerodha Kite Connect.
 *
 * Authentication via KiteSessionManager (secrets.properties + store.enc).
 * Rate-limited by data.api_call_delay_ms (default 100 ms between calls).
 */
public class KitePriceDataProvider implements PriceDataProvider {

    private static final Logger LOG = Logger.getLogger(KitePriceDataProvider.class.getName());

    private final MomentumConfig    config;
    private KiteConnect             kite;
    private final Map<String, Long> symbolToToken = new HashMap<>();
    private final long              apiDelayMs;

    // Running counters for the current fetch session
    private int fetchedCount  = 0;
    private int skippedNoToken = 0;
    private int skippedNoData  = 0;
    private int fetchErrors    = 0;

    public KitePriceDataProvider(MomentumConfig config) {
        this.config    = config;
        this.apiDelayMs = config.getApiCallDelayMs();
        LOG.info("KitePriceDataProvider created — api_call_delay_ms=" + apiDelayMs);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void init() throws Exception {
        LOG.info("KitePriceDataProvider.init() — authenticating via KiteSessionManager");
        kite = KiteSessionManager.getConnection();

        // ---- Token validity check ----
        // Make a lightweight profile call before bulk fetching.
        // If the token is expired Kite returns HTTP 403 immediately here,
        // giving a clear error rather than silently failing 500+ data calls.
        LOG.info("Verifying access token via getProfile()...");
        try {
            com.zerodhatech.models.Profile profile = kite.getProfile();
            LOG.info("Token valid — logged in as: "
                    + profile.userName + " (" + profile.userShortname + ")"
                    + "  email=" + profile.email
                    + "  broker=" + profile.broker);
        } catch (com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException ke) {
            String msg = ke.getMessage() != null ? ke.getMessage().toLowerCase() : "";
            if (msg.contains("token") || msg.contains("invalid") || msg.contains("403")
                    || ke.code == 403) {
                LOG.severe("ACCESS TOKEN EXPIRED or INVALID (HTTP " + ke.code + "): " + ke.getMessage());
                throw new Exception(
                    "Access token is expired or invalid.\n"
                    + "Run  update-tokens.bat  in C:\\bot  to refresh your daily token,\n"
                    + "then restart the application.\n"
                    + "(Kite error: " + ke.getMessage() + ")");
            }
            LOG.severe("getProfile() failed (KiteException " + ke.code + "): " + ke.getMessage());
            throw new Exception("Kite API error during token check: " + ke.getMessage(), ke);
        } catch (Throwable t) {
            LOG.severe("getProfile() failed unexpectedly: " + t.getMessage());
            throw new Exception("Failed to verify Kite token: " + t.getMessage(), t);
        }

        // ---- Instrument catalogue ----
        LOG.info("Fetching NSE instrument catalogue from Kite...");
        long t0 = System.currentTimeMillis();
        List<Instrument> instruments;
        try {
            instruments = kite.getInstruments("NSE");
        } catch (Throwable t) {
            LOG.severe("getInstruments(NSE) failed: " + t.getMessage());
            throw new Exception("Failed to load NSE instruments: " + t.getMessage(), t);
        }
        long elapsed = System.currentTimeMillis() - t0;

        int mapped = 0;
        for (Instrument inst : instruments) {
            if (inst.tradingsymbol != null && inst.instrument_token > 0) {
                symbolToToken.put(inst.tradingsymbol.trim().toUpperCase(), inst.instrument_token);
                mapped++;
            }
        }
        LOG.info("Instrument catalogue loaded: total=" + instruments.size()
                + "  mapped=" + mapped
                + "  elapsed=" + elapsed + "ms");

        if (mapped == 0) {
            LOG.severe("Instrument catalogue is EMPTY — cannot map any symbols to tokens");
            throw new Exception("NSE instrument catalogue returned 0 instruments.");
        }

        // ---- Sanity fetch: verify daily data works before bulk run ----
        // Fetch RELIANCE for the last 7 calendar days.
        // If this returns empty we know immediately that historical data is broken,
        // saving 5+ minutes of silent failures across 549 symbols.
        LOG.info("Sanity check: fetching RELIANCE daily data for last 7 days...");
        Long relianceToken = symbolToToken.get("RELIANCE");
        if (relianceToken != null) {
            Calendar sanCal = Calendar.getInstance();
            Date sanTo   = sanCal.getTime();
            sanCal.add(Calendar.DATE, -10);   // 10 calendar days back covers ~7 trading days
            Date sanFrom = sanCal.getTime();
            try {
                HistoricalData sanResult;
                try {
                    sanResult = kite.getHistoricalData(
                            sanFrom, sanTo, String.valueOf(relianceToken), "day", false, false);
                } catch (com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException ke) {
                    LOG.severe("Sanity fetch KiteException: code=" + ke.code + " msg=" + ke.getMessage());
                    throw new Exception("Kite API error on sanity fetch: " + ke.getMessage(), ke);
                }

                if (sanResult == null) {
                    LOG.severe("Sanity fetch: getHistoricalData returned NULL for RELIANCE. "
                            + "The Kite SDK may have an issue with the 'day' interval.");
                    throw new Exception("Kite getHistoricalData returned null for RELIANCE — "
                            + "check SDK version or interval parameter.");
                }

                int candles = (sanResult.dataArrayList != null) ? sanResult.dataArrayList.size() : 0;
                if (candles == 0) {
                    LOG.severe("Sanity fetch: RELIANCE returned 0 candles for range ["
                            + fmtDate(sanFrom) + " → " + fmtDate(sanTo) + "]. "
                            + "Possible causes: (1) today is a weekend/holiday, "
                            + "(2) the 'day' interval string is wrong, "
                            + "(3) Kite API returned data in an unexpected structure.");
                    // Log raw result for debugging
                    LOG.severe("Raw HistoricalData object: dataArrayList="
                            + sanResult.dataArrayList
                            + "  open=" + sanResult.open
                            + "  close=" + sanResult.close);
                    throw new Exception(
                        "RELIANCE daily data returned 0 candles — bulk fetch will also return 0.\n"
                        + "Check: (1) Is today a trading holiday? "
                        + "(2) Try a different date range.");
                }

                LOG.info("Sanity fetch OK — RELIANCE returned " + candles + " daily candles"
                        + "  lastClose=" + sanResult.dataArrayList.get(candles - 1).close
                        + "  lastDate="  + sanResult.dataArrayList.get(candles - 1).timeStamp);

            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("candles")) throw e; // rethrow our own
                LOG.severe("Sanity fetch FAILED for RELIANCE: " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
                throw new Exception("Kite historical data sanity check failed: " + e.getMessage(), e);
            }
        } else {
            LOG.warning("RELIANCE not found in instrument catalogue — skipping sanity fetch");
        }

        // Reset session counters
        fetchedCount   = 0;
        skippedNoToken = 0;
        skippedNoData  = 0;
        fetchErrors    = 0;
    }

    @Override
    public void close() {
        int total = fetchedCount + skippedNoToken + skippedNoData + fetchErrors;
        LOG.info("KitePriceDataProvider.close() — session summary:"
                + "  fetched="  + fetchedCount
                + "  noToken="  + skippedNoToken
                + "  noData="   + skippedNoData
                + "  errors="   + fetchErrors
                + "  total="    + total);

        if (fetchErrors > 0 && fetchedCount == 0) {
            LOG.severe("ALL " + fetchErrors + " data fetches failed with errors. "
                    + "Most likely cause: access token expired. "
                    + "Run update-tokens.bat in C:\\bot and restart.");
        } else if (fetchErrors > 0) {
            LOG.warning(fetchErrors + " symbols had fetch errors — check WARNING lines above for details.");
        }
        if (skippedNoToken > 0) {
            LOG.warning(skippedNoToken + " symbols had no Kite instrument token — "
                    + "run TokenExporter to identify them.");
        }
        if (fetchedCount == 0) {
            LOG.severe("No data was fetched for ANY symbol. Backtest will fail. "
                    + "Check token expiry and network connectivity.");
        }
    }

    // -------------------------------------------------------------------------
    // Data fetch
    // -------------------------------------------------------------------------

    @Override
    public List<DailyPrice> getDailyPrices(String symbol, Date from, Date to) throws Exception {
        symbol = symbol.trim().toUpperCase();

        Long token = symbolToToken.get(symbol);
        if (token == null) {
            LOG.warning("No NSE instrument token for symbol: " + symbol + " — skipped");
            skippedNoToken++;
            return Collections.emptyList();
        }

        LOG.info("Fetching [" + symbol + "]  token=" + token
                + "  from=" + fmtDate(from) + "  to=" + fmtDate(to));

        HistoricalData result;
        try {
            result = kite.getHistoricalData(from, to, String.valueOf(token), "day", false, false);
        } catch (Throwable t) {
            LOG.warning("getHistoricalData FAILED for " + symbol
                    + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
            fetchErrors++;
            return Collections.emptyList();
        }

        if (result == null) {
            LOG.warning("getHistoricalData returned NULL for " + symbol);
            skippedNoData++;
            return Collections.emptyList();
        }

        if (result.dataArrayList == null || result.dataArrayList.isEmpty()) {
            LOG.warning("No daily candles returned for " + symbol
                    + "  range=[" + fmtDate(from) + " → " + fmtDate(to) + "]"
                    + "  result.open=" + result.open + "  result.close=" + result.close);
            skippedNoData++;
            return Collections.emptyList();
        }

        List<DailyPrice> prices = new ArrayList<>();
        int parseErrors = 0;
        for (HistoricalData bar : result.dataArrayList) {
            Date barDate = parseDate(bar.timeStamp);
            if (barDate == null) { parseErrors++; continue; }
            prices.add(new DailyPrice(symbol, barDate,
                    bar.open, bar.high, bar.low, bar.close, bar.volume));
        }
        prices.sort(Comparator.comparing(p -> p.date));

        LOG.info("  OK " + symbol + " — " + prices.size() + " candles"
                + (parseErrors > 0 ? "  parseErrors=" + parseErrors : "")
                + "  first=" + fmtDate(prices.get(0).date)
                + "  last="  + fmtDate(prices.get(prices.size() - 1).date)
                + "  lastClose=" + prices.get(prices.size() - 1).close);

        fetchedCount++;
        sleep();
        return prices;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Date parseDate(String ts) {
        if (ts == null || ts.isEmpty()) return null;
        String[] formats = {
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        };
        for (String fmt : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt);
                if (!fmt.contains("Z")) sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
                return sdf.parse(ts);
            } catch (Exception ignored) {}
        }
        LOG.warning("Cannot parse Kite timestamp: '" + ts + "'");
        return null;
    }

    private void sleep() {
        if (apiDelayMs <= 0) return;
        try { Thread.sleep(apiDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String fmtDate(Date d) {
        if (d == null) return "null";
        return new SimpleDateFormat("yyyy-MM-dd").format(d);
    }
}
