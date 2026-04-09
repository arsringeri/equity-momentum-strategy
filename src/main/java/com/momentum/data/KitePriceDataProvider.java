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

        // Reset session counters
        fetchedCount   = 0;
        skippedNoToken = 0;
        skippedNoData  = 0;
        fetchErrors    = 0;
    }

    @Override
    public void close() {
        LOG.info("KitePriceDataProvider.close() — session summary:"
                + "  fetched="     + fetchedCount
                + "  noToken="     + skippedNoToken
                + "  noData="      + skippedNoData
                + "  errors="      + fetchErrors);
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

        LOG.fine("Fetching daily prices — symbol=" + symbol
                + "  token=" + token
                + "  from=" + fmtDate(from)
                + "  to=" + fmtDate(to));

        HistoricalData result;
        try {
            result = kite.getHistoricalData(from, to, String.valueOf(token), "day", false, false);
        } catch (Throwable t) {
            LOG.warning("getHistoricalData FAILED for " + symbol + ": " + t.getMessage());
            fetchErrors++;
            return Collections.emptyList();
        }

        if (result == null || result.dataArrayList == null || result.dataArrayList.isEmpty()) {
            LOG.fine("No data returned for " + symbol
                    + " in range [" + fmtDate(from) + ", " + fmtDate(to) + "]");
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

        LOG.fine("Fetched " + prices.size() + " daily bars for " + symbol
                + (parseErrors > 0 ? "  (parseErrors=" + parseErrors + ")" : "")
                + "  first=" + fmtDate(prices.get(0).date)
                + "  last="  + fmtDate(prices.get(prices.size() - 1).date));

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
