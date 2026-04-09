package com.momentum.engine;

import com.momentum.config.MomentumConfig;
import com.momentum.data.PriceDataProvider;
import com.momentum.data.UniverseLoader;
import com.momentum.model.*;
import com.momentum.strategy.MomentumStrategy;

import java.util.*;
import java.util.logging.Logger;

/**
 * Core simulation engine.
 *
 * Flow:
 *  1. Load universe (symbols from CSV)
 *  2. For each symbol fetch daily prices: [fromDate - lookbackDays, toDate]
 *  3. For each Monday in [fromDate, toDate]:
 *      a. Run strategy.selectStocks() to get top-N
 *      b. Diff against current holdings → generate BUY/SELL trades
 *      c. Execute trades at Monday's close price
 *      d. Record WeeklySummary
 *  4. For every calendar day, mark-to-market the portfolio and record PortfolioSnapshot
 *  5. Compute performance metrics via PerformanceCalculator
 *
 * Thread safety:
 *   This class is NOT thread-safe. Run one instance per backtest call.
 *
 * Progress reporting:
 *   Implement the ProgressListener to receive per-symbol fetch updates
 *   (used by SwingWorker to update the UI progress bar).
 */
public class BacktestEngine {

    private static final Logger LOG = Logger.getLogger(BacktestEngine.class.getName());

    public interface ProgressListener {
        /** Called after each symbol's data is fetched. symbolsDone / symbolsTotal. */
        void onProgress(int symbolsDone, int symbolsTotal, String currentSymbol);
    }

    private final MomentumConfig   config;
    private final PriceDataProvider dataProvider;
    private final MomentumStrategy  strategy;

    public BacktestEngine(MomentumConfig config,
                          PriceDataProvider dataProvider,
                          MomentumStrategy strategy) {
        this.config       = config;
        this.dataProvider = dataProvider;
        this.strategy     = strategy;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Run the full backtest.
     *
     * @param fromDate        start of the simulation window
     * @param toDate          end of the simulation window
     * @param listener        progress callback (may be null)
     * @return                complete backtest result
     */
    public BacktestResult run(Date fromDate, Date toDate, ProgressListener listener) throws Exception {
        // 1. Load universe
        List<String> universe = UniverseLoader.load(config.getUniverseFile());
        LOG.info("Universe size: " + universe.size());

        // 2. Fetch price history for each stock
        Date fetchFrom = subtractDays(fromDate, config.getLookbackExtraDays());
        Map<String, List<DailyPrice>> priceHistory = new HashMap<>();

        dataProvider.init();
        try {
            int total = universe.size();
            int done  = 0;
            for (String symbol : universe) {
                if (listener != null) listener.onProgress(done, total, symbol);
                try {
                    List<DailyPrice> prices = dataProvider.getDailyPrices(symbol, fetchFrom, toDate);
                    if (!prices.isEmpty()) {
                        priceHistory.put(symbol, prices);
                    }
                } catch (Exception e) {
                    LOG.warning("Skipping " + symbol + ": " + e.getMessage());
                }
                done++;
                if (listener != null) listener.onProgress(done, total, symbol);
            }
        } finally {
            dataProvider.close();
        }
        LOG.info("Fetched data for " + priceHistory.size() + " symbols.");

        // 3. Identify all Mondays in [fromDate, toDate]
        List<Date> rebalanceDates = getRebalanceDates(fromDate, toDate, config.getRebalanceDay());
        LOG.info("Rebalance dates: " + rebalanceDates.size());

        // 4. Simulation loop
        double initialCapital = config.getInitialCapital();
        double cash           = initialCapital;
        Map<String, Double> holdings = new LinkedHashMap<>();  // symbol → shares

        List<PortfolioSnapshot> dailySnapshots  = new ArrayList<>();
        List<WeeklySummary>     weeklySummaries = new ArrayList<>();
        List<Trade>             trades          = new ArrayList<>();

        // Build a lookup: for each date, get a set of all available daily closes
        // Pre-index price history by symbol + date for O(1) lookup
        Map<String, Map<Date, DailyPrice>> priceIndex = buildPriceIndex(priceHistory);

        List<Date> allDays = getAllTradingDays(priceHistory, fromDate, toDate);

        int weekNum         = 0;
        int rebalIdx        = 0;
        double prevWeekNAV  = initialCapital;

        for (Date day : allDays) {
            // Check if today is a rebalance day
            if (rebalIdx < rebalanceDates.size() && sameDay(day, rebalanceDates.get(rebalIdx))) {
                weekNum++;
                Date rebalDate = rebalanceDates.get(rebalIdx);
                rebalIdx++;

                // Select stocks
                List<StockReturn> selected = strategy.selectStocks(priceHistory, rebalDate);
                Set<String> newHoldings    = new HashSet<>();
                for (StockReturn sr : selected) newHoldings.add(sr.symbol);

                Set<String> currentHoldings = new HashSet<>(holdings.keySet());

                // Stocks to remove
                List<String> removed = new ArrayList<>();
                for (String sym : currentHoldings) {
                    if (!newHoldings.contains(sym)) removed.add(sym);
                }

                // Stocks to add
                List<String> added = new ArrayList<>();
                for (String sym : newHoldings) {
                    if (!currentHoldings.contains(sym)) added.add(sym);
                }

                // Execute sells first
                for (String sym : removed) {
                    double price = getClose(priceIndex, sym, rebalDate);
                    if (price <= 0) price = getLastKnownClose(priceHistory, sym, rebalDate);
                    if (price <= 0) continue;
                    double shares = holdings.getOrDefault(sym, 0.0);
                    cash += shares * price;
                    holdings.remove(sym);
                    trades.add(new Trade(rebalDate, sym, Trade.Direction.SELL, shares, price));
                }

                // Compute total portfolio value (cash + current mark-to-market)
                double mtm = cash;
                for (Map.Entry<String, Double> h : holdings.entrySet()) {
                    double price = getClose(priceIndex, h.getKey(), rebalDate);
                    if (price <= 0) price = getLastKnownClose(priceHistory, h.getKey(), rebalDate);
                    mtm += h.getValue() * price;
                }

                // Equal weight: 1/topN of total portfolio value per stock
                int    n           = selected.size();
                double targetPerStock = (n > 0) ? mtm / n : 0;

                // Rebalance existing holdings and buy new entrants
                for (StockReturn sr : selected) {
                    double price = getClose(priceIndex, sr.symbol, rebalDate);
                    if (price <= 0) price = getLastKnownClose(priceHistory, sr.symbol, rebalDate);
                    if (price <= 0) {
                        newHoldings.remove(sr.symbol);
                        continue;
                    }
                    double targetShares = targetPerStock / price;
                    double currentShares = holdings.getOrDefault(sr.symbol, 0.0);
                    double delta = targetShares - currentShares;

                    if (Math.abs(delta) < 0.001) continue;

                    if (delta > 0) {
                        double cost = delta * price;
                        if (cost > cash) {
                            // Buy as many as cash allows
                            delta = cash / price;
                            cost  = delta * price;
                        }
                        cash -= cost;
                        trades.add(new Trade(rebalDate, sr.symbol, Trade.Direction.BUY, delta, price));
                    } else {
                        cash += Math.abs(delta) * price;
                        trades.add(new Trade(rebalDate, sr.symbol, Trade.Direction.SELL, Math.abs(delta), price));
                    }
                    holdings.put(sr.symbol, currentShares + delta);
                }

                // Weekly summary
                double nav = computeNAV(cash, holdings, priceIndex, priceHistory, day);
                double weekReturn = (prevWeekNAV > 0) ? (nav - prevWeekNAV) / prevWeekNAV : Double.NaN;
                weeklySummaries.add(new WeeklySummary(weekNum, rebalDate, nav, weekReturn, added, removed));
                prevWeekNAV = nav;
            }

            // Daily mark-to-market snapshot
            double nav = computeNAV(cash, holdings, priceIndex, priceHistory, day);
            dailySnapshots.add(new PortfolioSnapshot(day, nav, new LinkedHashMap<>(holdings)));
        }

        // 5. Compute metrics
        Map<String, Double> metrics = PerformanceCalculator.compute(dailySnapshots, trades.size());

        return new BacktestResult(dailySnapshots, weeklySummaries, trades, metrics);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private double computeNAV(double cash, Map<String, Double> holdings,
                               Map<String, Map<Date, DailyPrice>> priceIndex,
                               Map<String, List<DailyPrice>> priceHistory, Date day) {
        double nav = cash;
        for (Map.Entry<String, Double> h : holdings.entrySet()) {
            double price = getClose(priceIndex, h.getKey(), day);
            if (price <= 0) price = getLastKnownClose(priceHistory, h.getKey(), day);
            nav += h.getValue() * price;
        }
        return nav;
    }

    /** O(1) close lookup by symbol + date. */
    private double getClose(Map<String, Map<Date, DailyPrice>> index, String symbol, Date date) {
        Map<Date, DailyPrice> byDate = index.get(symbol);
        if (byDate == null) return 0;
        DailyPrice p = byDate.get(stripTime(date));
        return (p != null) ? p.close : 0;
    }

    /** Forward-fill: returns last known close on or before cutoff. */
    private double getLastKnownClose(Map<String, List<DailyPrice>> history, String symbol, Date cutoff) {
        List<DailyPrice> prices = history.get(symbol);
        if (prices == null || prices.isEmpty()) return 0;
        double last = 0;
        for (DailyPrice p : prices) {
            if (p.date.after(cutoff)) break;
            last = p.close;
        }
        return last;
    }

    private Map<String, Map<Date, DailyPrice>> buildPriceIndex(Map<String, List<DailyPrice>> history) {
        Map<String, Map<Date, DailyPrice>> index = new HashMap<>();
        for (Map.Entry<String, List<DailyPrice>> e : history.entrySet()) {
            Map<Date, DailyPrice> byDate = new HashMap<>();
            for (DailyPrice p : e.getValue()) {
                byDate.put(stripTime(p.date), p);
            }
            index.put(e.getKey(), byDate);
        }
        return index;
    }

    /** Returns all trading days (days where at least one stock has data) in [from, to]. */
    private List<Date> getAllTradingDays(Map<String, List<DailyPrice>> history, Date from, Date to) {
        TreeSet<Date> days = new TreeSet<>();
        for (List<DailyPrice> prices : history.values()) {
            for (DailyPrice p : prices) {
                Date d = stripTime(p.date);
                if (!d.before(stripTime(from)) && !d.after(stripTime(to))) {
                    days.add(d);
                }
            }
        }
        return new ArrayList<>(days);
    }

    /** Enumerates all Mondays (or configured rebalance day) in [from, to]. */
    private List<Date> getRebalanceDates(Date from, Date to, String dayName) {
        int targetDow = dayNameToCalendar(dayName);
        List<Date> dates = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.setTime(from);
        // Advance to first target day
        while (cal.get(Calendar.DAY_OF_WEEK) != targetDow) cal.add(Calendar.DATE, 1);
        while (!cal.getTime().after(to)) {
            dates.add(stripTime(cal.getTime()));
            cal.add(Calendar.DATE, 7);
        }
        return dates;
    }

    private int dayNameToCalendar(String name) {
        switch (name.toUpperCase()) {
            case "TUESDAY":   return Calendar.TUESDAY;
            case "WEDNESDAY": return Calendar.WEDNESDAY;
            case "THURSDAY":  return Calendar.THURSDAY;
            case "FRIDAY":    return Calendar.FRIDAY;
            default:          return Calendar.MONDAY;
        }
    }

    private boolean sameDay(Date a, Date b) {
        return stripTime(a).equals(stripTime(b));
    }

    private Date stripTime(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private Date subtractDays(Date d, int days) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.add(Calendar.DATE, -days);
        return c.getTime();
    }
}
