package com.momentum.data;

import com.momentum.config.MomentumConfig;
import com.momentum.model.DailyPrice;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Instrument;

import java.io.BufferedReader;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * Fetches daily OHLCV data from Zerodha Kite Connect historical API.
 *
 * Authentication:
 *   - api_key from momentum.properties (kite.api_key)
 *   - access_token from kite.token_file (default C:/Input/store.txt, line 2)
 *     This is the same store.txt used by C:/bot — no duplicate login required.
 *
 * Rate limiting:
 *   - Sleeps data.api_call_delay_ms between calls (default 100ms).
 *
 * Instrument token resolution:
 *   - On init(), fetches the full NSE instrument list from Kite and builds
 *     a symbol → instrument_token map. The token is needed for historical data queries.
 */
public class KitePriceDataProvider implements PriceDataProvider {

    private static final Logger LOG = Logger.getLogger(KitePriceDataProvider.class.getName());

    private final MomentumConfig config;
    private KiteConnect          kite;
    private final Map<String, Long> symbolToToken = new HashMap<>();
    private final long apiDelayMs;

    public KitePriceDataProvider(MomentumConfig config) {
        this.config     = config;
        this.apiDelayMs = config.getApiCallDelayMs();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void init() throws Exception {
        String apiKey      = config.getApiKey();
        String accessToken = readAccessToken(config.getTokenFile());

        if (apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY_HERE")) {
            throw new IllegalStateException(
                "kite.api_key is not set in momentum.properties. "
                + "Please enter your Zerodha API key.");
        }
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IllegalStateException(
                "Could not read access_token from " + config.getTokenFile()
                + ". Ensure C:/bot is logged in (store.txt must contain the token on line 2).");
        }

        kite = new KiteConnect(apiKey);
        kite.setAccessToken(accessToken);

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
            LOG.warning("No instrument token for symbol: " + symbol + " — skipping.");
            return Collections.emptyList();
        }

        // Kite historical API signature (SDK 3.5.1):
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

        // Sort ascending by date (should already be, but be safe)
        prices.sort(Comparator.comparing(p -> p.date));

        sleep();
        return prices;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Reads access_token from store.txt.
     * File format (same as C:/bot's store.txt):
     *   line 1: api_secret (ignored here)
     *   line 2: access_token
     *   line 3: public_token (ignored here)
     */
    private String readAccessToken(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); // skip api_secret
            String token = br.readLine();
            return token != null ? token.trim() : null;
        } catch (Exception e) {
            LOG.warning("Cannot read access_token from " + path + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses Zerodha's timeStamp string.
     * Supported formats:
     *   "2024-03-05T09:15:00+0530"  (ISO with numeric TZ offset)
     *   "2024-03-05 09:15:00"       (space-separated, no TZ)
     *   "2024-03-05"                (date only, for daily bars)
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
                if (fmt.equals("yyyy-MM-dd HH:mm:ss") || fmt.equals("yyyy-MM-dd")) {
                    sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
                }
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
