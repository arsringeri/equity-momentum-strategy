package com.momentum.engine;

import com.momentum.config.MomentumConfig;
import com.momentum.data.PriceDataProvider;
import com.momentum.data.UniverseLoader;
import com.momentum.model.*;
import com.momentum.strategy.MomentumStrategy;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * Core simulation engine.
 *
 * Flow:
 *  1. Load universe from CSV
 *  2. Fetch daily prices [fromDate - lookbackDays, toDate] for every symbol
 *  3. For each Monday in [fromDate, toDate]:
 *       rank stocks → select top-N → diff vs current holdings → execute trades
 *  4. For every trading day: mark-to-market the portfolio (daily NAV snapshot)
 *  5. Compute performance metrics
 */
public class BacktestEngine {

    private static final Logger LOG = Logger.getLogger(BacktestEngine.class.getName());

    public interface ProgressListener {
        void onProgress(int symbolsDone, int symbolsTotal, String currentSymbol);
    }

    private final MomentumConfig    config;
    private final PriceDataProvider dataProvider;
    private final MomentumStrategy  strategy;

    public BacktestEngine(MomentumConfig config,
                          PriceDataProvider dataProvider,
                          MomentumStrategy strategy) {
        this.config       = config;
        this.dataProvider = dataProvider;
        this.strategy     = strategy;
        LOG.info("BacktestEngine created — strategy=" + strategy.getName());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public BacktestResult run(Date fromDate, Date toDate, ProgressListener listener) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        LOG.info("BacktestEngine.run() START"
                + "  from=" + sdf.format(fromDate)
                + "  to="   + sdf.format(toDate)
                + "  strategy=" + strategy.getName()
                + "  initialCapital=" + String.format("%,.2f", config.getInitialCapital()));

        // 1. Load universe
        List<String> universe = UniverseLoader.load(config.getUniverseFile());
        LOG.info("Universe loaded: " + universe.size() + " symbols");

        // 2. Fetch price history
        Date fetchFrom = subtractDays(fromDate, config.getLookbackExtraDays());
        LOG.info("Fetching price history from " + sdf.format(fetchFrom)
                + " to " + sdf.format(toDate)
                + " (lookback=" + config.getLookbackExtraDays() + " extra days)");

        Map<String, List<DailyPrice>> priceHistory = new HashMap<>();
        long fetchStart = System.currentTimeMillis();

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
                    LOG.warning("Skipping " + symbol + " due to fetch error: " + e.getMessage());
                }
                done++;
                if (listener != null) listener.onProgress(done, total, symbol);
            }
        } finally {
            dataProvider.close();
        }

        long fetchElapsed = System.currentTimeMillis() - fetchStart;
        LOG.info("Price fetch complete: " + priceHistory.size() + "/" + universe.size()
                + " symbols with data  (" + fetchElapsed + "ms)");

        if (priceHistory.isEmpty()) {
            LOG.severe("No price data available — cannot run backtest");
            throw new RuntimeException("No price data available for any symbol in the universe.");
        }

        // 3. Identify rebalance dates (Mondays)
        List<Date> rebalanceDates = getRebalanceDates(fromDate, toDate, config.getRebalanceDay());
        LOG.info("Rebalance dates: " + rebalanceDates.size()
                + "  first=" + (rebalanceDates.isEmpty() ? "none" : sdf.format(rebalanceDates.get(0)))
                + "  last="  + (rebalanceDates.isEmpty() ? "none" : sdf.format(rebalanceDates.get(rebalanceDates.size() - 1))));

        // 4. Simulation loop
        double initialCapital = config.getInitialCapital();
        double cash           = initialCapital;
        Map<String, Double> holdings = new LinkedHashMap<>();

        List<PortfolioSnapshot> dailySnapshots  = new ArrayList<>();
        List<WeeklySummary>     weeklySummaries = new ArrayList<>();
        List<Trade>             trades          = new ArrayList<>();

        Map<String, Map<Date, DailyPrice>> priceIndex = buildPriceIndex(priceHistory);
        List<Date> allDays = getAllTradingDays(priceHistory, fromDate, toDate);

        LOG.info("Simulation loop starting: " + allDays.size() + " trading days");

        int    weekNum      = 0;
        int    rebalIdx     = 0;
        double prevWeekNAV  = initialCapital;

        for (Date day : allDays) {

            // ---- Rebalance on Mondays ----
            if (rebalIdx < rebalanceDates.size() && sameDay(day, rebalanceDates.get(rebalIdx))) {
                weekNum++;
                Date rebalDate = rebalanceDates.get(rebalIdx);
                rebalIdx++;

                LOG.info("--- REBALANCE week=" + weekNum + "  date=" + sdf.format(rebalDate)
                        + "  cash=" + String.format("%,.2f", cash)
                        + "  holdings=" + holdings.size() + " stocks ---");

                // Select new portfolio
                List<StockReturn> selected = strategy.selectStocks(priceHistory, rebalDate);
                Set<String> newHoldings = new HashSet<>();
                for (StockReturn sr : selected) newHoldings.add(sr.symbol);

                Set<String> currentHoldings = new HashSet<>(holdings.keySet());
                List<String> removed = new ArrayList<>();
                List<String> added   = new ArrayList<>();
                for (String sym : currentHoldings) if (!newHoldings.contains(sym)) removed.add(sym);
                for (String sym : newHoldings) if (!currentHoldings.contains(sym)) added.add(sym);

                LOG.info("  Rebalance diff: added=" + added.size()
                        + " " + added
                        + "  removed=" + removed.size()
                        + " " + removed);

                // Execute sells
                double sellProceeds = 0;
                for (String sym : removed) {
                    double price = getClose(priceIndex, sym, rebalDate);
                    if (price <= 0) price = getLastKnownClose(priceHistory, sym, rebalDate);
                    if (price <= 0) {
                        LOG.warning("  SELL skipped — no price for " + sym + " on " + sdf.format(rebalDate));
                        continue;
                    }
                    double shares = holdings.getOrDefault(sym, 0.0);
                    double proceeds = shares * price;
                    cash += proceeds;
                    sellProceeds += proceeds;
                    holdings.remove(sym);
                    trades.add(new Trade(rebalDate, sym, Trade.Direction.SELL, shares, price));
                    LOG.fine("  SELL " + sym + "  shares=" + String.format("%.2f", shares)
                            + "  price=" + String.format("%.2f", price)
                            + "  proceeds=" + String.format("%,.2f", proceeds));
                }
                if (!removed.isEmpty()) {
                    LOG.info("  Sells complete: " + removed.size() + " stocks"
                            + "  proceeds=" + String.format("%,.2f", sellProceeds)
                            + "  cash after=" + String.format("%,.2f", cash));
                }

                // Mark-to-market total portfolio value for equal-weight target
                double mtm = cash;
                for (Map.Entry<String, Double> h : holdings.entrySet()) {
                    double p = getClose(priceIndex, h.getKey(), rebalDate);
                    if (p <= 0) p = getLastKnownClose(priceHistory, h.getKey(), rebalDate);
                    mtm += h.getValue() * p;
                }
                int    n              = selected.size();
                double targetPerStock = (n > 0) ? mtm / n : 0;
                LOG.info("  Portfolio MTM=" + String.format("%,.2f", mtm)
                        + "  targetPerStock=" + String.format("%,.2f", targetPerStock)
                        + "  n=" + n);

                // Rebalance / buy
                int buyCount = 0; int sellAdjCount = 0;
                for (StockReturn sr : selected) {
                    double price = getClose(priceIndex, sr.symbol, rebalDate);
                    if (price <= 0) price = getLastKnownClose(priceHistory, sr.symbol, rebalDate);
                    if (price <= 0) {
                        LOG.warning("  BUY skipped — no price for " + sr.symbol
                                + " on " + sdf.format(rebalDate));
                        newHoldings.remove(sr.symbol);
                        continue;
                    }
                    double targetShares  = targetPerStock / price;
                    double currentShares = holdings.getOrDefault(sr.symbol, 0.0);
                    double delta         = targetShares - currentShares;

                    if (Math.abs(delta) < 0.001) continue;

                    if (delta > 0) {
                        double cost = delta * price;
                        if (cost > cash) {
                            LOG.warning("  Insufficient cash for full BUY of " + sr.symbol
                                    + "  needed=" + String.format("%,.2f", cost)
                                    + "  available=" + String.format("%,.2f", cash));
                            delta = cash / price;
                            cost  = delta * price;
                        }
                        cash -= cost;
                        buyCount++;
                        trades.add(new Trade(rebalDate, sr.symbol, Trade.Direction.BUY, delta, price));
                        LOG.fine("  BUY  " + sr.symbol
                                + "  shares=" + String.format("%.2f", delta)
                                + "  price=" + String.format("%.2f", price)
                                + "  cost=" + String.format("%,.2f", cost));
                    } else {
                        double proceeds = Math.abs(delta) * price;
                        cash += proceeds;
                        sellAdjCount++;
                        trades.add(new Trade(rebalDate, sr.symbol, Trade.Direction.SELL, Math.abs(delta), price));
                        LOG.fine("  SELL-ADJ " + sr.symbol
                                + "  shares=" + String.format("%.2f", Math.abs(delta))
                                + "  price=" + String.format("%.2f", price)
                                + "  proceeds=" + String.format("%,.2f", proceeds));
                    }
                    holdings.put(sr.symbol, currentShares + delta);
                }
                LOG.info("  Buys/adjustments: buys=" + buyCount
                        + "  sellAdj=" + sellAdjCount
                        + "  cashAfter=" + String.format("%,.2f", cash));

                // Weekly summary
                double nav        = computeNAV(cash, holdings, priceIndex, priceHistory, day);
                double weekReturn = (prevWeekNAV > 0) ? (nav - prevWeekNAV) / prevWeekNAV : Double.NaN;
                weeklySummaries.add(new WeeklySummary(weekNum, rebalDate, nav, weekReturn, added, removed));
                prevWeekNAV = nav;

                LOG.info("  Week " + weekNum + " post-rebalance NAV=" + String.format("%,.2f", nav)
                        + "  weekReturn=" + (Double.isNaN(weekReturn) ? "N/A"
                            : String.format("%+.2f%%", weekReturn * 100))
                        + "  trades_total=" + trades.size());
            }

            // ---- Daily NAV snapshot ----
            double nav = computeNAV(cash, holdings, priceIndex, priceHistory, day);
            dailySnapshots.add(new PortfolioSnapshot(day, nav, new LinkedHashMap<>(holdings)));
            LOG.fine("  NAV [" + sdf.format(day) + "] = " + String.format("%,.2f", nav));
        }

        LOG.info("Simulation loop complete:"
                + "  days=" + dailySnapshots.size()
                + "  weeks=" + weeklySummaries.size()
                + "  trades=" + trades.size());

        // 5. Metrics
        Map<String, Double> metrics = PerformanceCalculator.compute(dailySnapshots, trades.size());

        BacktestResult result = new BacktestResult(dailySnapshots, weeklySummaries, trades, metrics);
        LOG.info("BacktestEngine.run() DONE — " + result);
        return result;
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
            if (price <= 0) {
                LOG.warning("computeNAV: no price for " + h.getKey() + " on "
                        + new SimpleDateFormat("yyyy-MM-dd").format(day)
                        + " — using 0 (holding=" + String.format("%.2f", h.getValue()) + " shares)");
            }
            nav += h.getValue() * price;
        }
        return nav;
    }

    private double getClose(Map<String, Map<Date, DailyPrice>> index, String symbol, Date date) {
        Map<Date, DailyPrice> byDate = index.get(symbol);
        if (byDate == null) return 0;
        DailyPrice p = byDate.get(stripTime(date));
        return (p != null) ? p.close : 0;
    }

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
            for (DailyPrice p : e.getValue()) byDate.put(stripTime(p.date), p);
            index.put(e.getKey(), byDate);
        }
        return index;
    }

    private List<Date> getAllTradingDays(Map<String, List<DailyPrice>> history, Date from, Date to) {
        TreeSet<Date> days = new TreeSet<>();
        for (List<DailyPrice> prices : history.values()) {
            for (DailyPrice p : prices) {
                Date d = stripTime(p.date);
                if (!d.before(stripTime(from)) && !d.after(stripTime(to))) days.add(d);
            }
        }
        return new ArrayList<>(days);
    }

    private List<Date> getRebalanceDates(Date from, Date to, String dayName) {
        int targetDow = dayNameToCalendar(dayName);
        List<Date> dates = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.setTime(from);
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

    private boolean sameDay(Date a, Date b) { return stripTime(a).equals(stripTime(b)); }

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
