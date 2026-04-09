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
 * Authentication:
 *   Delegates entirely to KiteSessionManager, which mirrors C:/bot's KiteSession:
 *     - API key + user ID from secrets.properties (in working directory)
 *     - access_token / public_token decrypted from C:\Input\store.enc
 *     - Swing password dialog on first use (up to 3 attempts)
 *   The same store.enc is shared with C:/bot — no duplicate login needed.
 *
 * Rate limiting:
 *   Sleeps data.api_call_delay_ms (default 100 ms) between API calls.
 */
public class KitePriceDataProvider implements PriceDataProvider {

    private static final Logger LOG = Logger.getLogger(KitePriceDataProvider.class.getName());

    private final MomentumConfig      config;
    private KiteConnect               kite;
    private final Map<String, Long>   symbolToToken = new HashMap<>();
    private final long                apiDelayMs;

    public KitePriceDataProvider(MomentumConfig config) {
        this.config     = config;
        this.apiDelayMs = config.getApiCallDelayMs();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void init() throws Exception {
        // KiteSessionManager handles secrets.properties + store.enc decryption
        kite = KiteSessionManager.getConnection();

        LOG.info("Loading NSE instrument catalogue from Kite...");
        List<Instrument> instruments;
        try {
            instruments = kite.getInstruments("NSE");
        } catch (Throwable t) {
            throw new Exception("Failed to load NSE instruments from Kite: " + t.getMessage(), t);
        }

        for (Instrument inst : instruments) {
            if (inst.tradingsymbol != null && inst.instrument_token > 0) {
                symbolToToken.put(inst.tradingsymbol.trim().toUpperCase(), inst.instrument_token);
            }
        }
        LOG.info("Loaded " + symbolToToken.size() + " NSE instruments.");
    }

    @Override
    public void close() {
        // KiteConnect HTTP client has no explicit close in SDK 3.5.1
    }

    // -------------------------------------------------------------------------
    // Data fetch
    // -------------------------------------------------------------------------

    @Override
    public List<DailyPrice> getDailyPrices(String symbol, Date from, Date to) throws Exception {
        symbol = symbol.trim().toUpperCase();
        Long token = symbolToToken.get(symbol);
        if (token == null) {
            LOG.warning("No instrument token for: " + symbol + " — skipping.");
            return Collections.emptyList();
        }

        // SDK 3.5.1 signature:
        // getHistoricalData(Date from, Date to, String token, String interval,
        //                   boolean continuous, boolean oi)
        HistoricalData result;
        try {
            result = kite.getHistoricalData(from, to, String.valueOf(token), "day", false, false);
        } catch (Throwable t) {
            LOG.warning("getHistoricalData failed for " + symbol + ": " + t.getMessage());
            return Collections.emptyList();
        }

        if (result == null || result.dataArrayList == null || result.dataArrayList.isEmpty()) {
            return Collections.emptyList();
        }

        List<DailyPrice> prices = new ArrayList<>();
        for (HistoricalData bar : result.dataArrayList) {
            Date barDate = parseDate(bar.timeStamp);
            if (barDate == null) continue;
            prices.add(new DailyPrice(symbol, barDate,
                    bar.open, bar.high, bar.low, bar.close, bar.volume));
        }
        prices.sort(Comparator.comparing(p -> p.date));

        sleep();
        return prices;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Parses Zerodha timeStamp string to a Date.
     * Handles:
     *   "2024-03-05T09:15:00+0530"  (ISO with numeric TZ)
     *   "2024-03-05 09:15:00"       (space-separated, no TZ)
     *   "2024-03-05"                (date only — used for daily bars)
     */
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
        LOG.warning("Cannot parse timestamp: " + ts);
        return null;
    }

    private void sleep() {
        if (apiDelayMs <= 0) return;
        try { Thread.sleep(apiDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
