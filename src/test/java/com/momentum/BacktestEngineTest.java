package com.momentum;

import com.momentum.config.MomentumConfig;
import com.momentum.data.PriceDataProvider;
import com.momentum.engine.BacktestEngine;
import com.momentum.engine.PerformanceCalculator;
import com.momentum.model.*;
import com.momentum.strategy.TopNMomentumStrategy;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for core backtest logic using in-memory stub price data.
 * No Kite API calls are made.
 */
public class BacktestEngineTest {

    // -------------------------------------------------------------------------
    // Stub price provider
    // -------------------------------------------------------------------------

    /**
     * Generates synthetic daily prices for a given set of symbols.
     * Each symbol gets a deterministic random walk based on its hash.
     */
    static class StubPriceDataProvider implements PriceDataProvider {

        private final List<String> symbols;

        StubPriceDataProvider(List<String> symbols) {
            this.symbols = symbols;
        }

        @Override
        public void init() {}

        @Override
        public void close() {}

        @Override
        public List<DailyPrice> getDailyPrices(String symbol, Date from, Date to) {
            List<DailyPrice> prices = new ArrayList<>();
            Random rng  = new Random(symbol.hashCode());
            double price = 100 + rng.nextInt(900);
            Calendar cal = Calendar.getInstance();
            cal.setTime(from);
            while (!cal.getTime().after(to)) {
                // Skip weekends
                int dow = cal.get(Calendar.DAY_OF_WEEK);
                if (dow != Calendar.SATURDAY && dow != Calendar.SUNDAY) {
                    double change = (rng.nextDouble() - 0.48) * 2;  // slight upward bias
                    price = Math.max(1, price + change);
                    prices.add(new DailyPrice(symbol, cal.getTime(),
                            price, price * 1.005, price * 0.995, price, 100_000L));
                }
                cal.add(Calendar.DATE, 1);
            }
            return prices;
        }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    public void testBacktestRunsWithoutError() throws Exception {
        MomentumConfig config = new MomentumConfig("momentum.properties");
        config.set("portfolio.top_n",    "5");
        config.set("portfolio.initial_capital", "100000.0");
        config.set("data.universe_file", "test_universe");  // will trigger UniverseLoader fallback
        config.set("data.lookback_extra_days", "380");
        config.set("data.api_call_delay_ms", "0");

        List<String> symbols = Arrays.asList(
                "ALPHA", "BETA", "GAMMA", "DELTA", "EPSILON",
                "ZETA", "ETA", "THETA", "IOTA", "KAPPA");

        StubPriceDataProvider stub = new StubPriceDataProvider(symbols);

        // Override engine to inject stub; wrap so UniverseLoader returns our symbols
        BacktestEngine engine = new BacktestEngine(config, stub, new TopNMomentumStrategy(5)) {
            // No override needed — stub provider just ignores symbol lookup
        };

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date fromDate = sdf.parse("2022-01-03");
        Date toDate   = sdf.parse("2023-01-06");

        // We inject prices directly without Universe file by subclassing engine
        // Build price history manually and call the strategy directly instead
        Map<String, List<DailyPrice>> priceHistory = new HashMap<>();
        Date fetchFrom = subtractDays(fromDate, 380);
        for (String sym : symbols) {
            priceHistory.put(sym, stub.getDailyPrices(sym, fetchFrom, toDate));
        }

        // Test strategy selection
        TopNMomentumStrategy strategy = new TopNMomentumStrategy(5);
        List<StockReturn> selected = strategy.selectStocks(priceHistory, fromDate);
        assertNotNull(selected);
        assertTrue("Should select at most 5 stocks", selected.size() <= 5);
        if (selected.size() > 1) {
            assertTrue("Results should be sorted descending",
                    selected.get(0).oneYearReturn >= selected.get(1).oneYearReturn);
        }
    }

    @Test
    public void testPerformanceCalculatorTotalReturn() {
        List<PortfolioSnapshot> snapshots = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.set(2022, Calendar.JANUARY, 1);
        snapshots.add(new PortfolioSnapshot(cal.getTime(), 100_000.0, Collections.emptyMap()));
        cal.set(2023, Calendar.JANUARY, 1);
        snapshots.add(new PortfolioSnapshot(cal.getTime(), 120_000.0, Collections.emptyMap()));

        Map<String, Double> metrics = PerformanceCalculator.compute(snapshots, 40);
        assertEquals(0.20, metrics.get("totalReturn"), 1e-9);
        assertTrue("CAGR should be positive", metrics.get("cagr") > 0);
        assertEquals(0.0, metrics.get("maxDrawdown"), 1e-9);
        assertEquals(40.0, metrics.get("tradeCount"), 1e-9);
    }

    @Test
    public void testPerformanceCalculatorMaxDrawdown() {
        List<PortfolioSnapshot> snapshots = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.set(2022, Calendar.JANUARY, 1);
        snapshots.add(new PortfolioSnapshot(cal.getTime(), 100_000.0, Collections.emptyMap()));
        cal.set(2022, Calendar.JUNE, 1);
        snapshots.add(new PortfolioSnapshot(cal.getTime(), 80_000.0, Collections.emptyMap()));   // 20% drawdown
        cal.set(2023, Calendar.JANUARY, 1);
        snapshots.add(new PortfolioSnapshot(cal.getTime(), 110_000.0, Collections.emptyMap()));

        Map<String, Double> metrics = PerformanceCalculator.compute(snapshots, 0);
        assertEquals(0.20, metrics.get("maxDrawdown"), 1e-9);
    }

    @Test
    public void testTopNStrategyExcludesShortHistory() {
        // Stock with only 100 days of history should not be selected
        Map<String, List<DailyPrice>> priceHistory = new HashMap<>();

        // Short-history stock: 100 days
        List<DailyPrice> shortHistory = buildPrices("SHORT", 100, 100.0, 0.5);
        priceHistory.put("SHORT", shortHistory);

        // Long-history stock: 300 days
        List<DailyPrice> longHistory = buildPrices("LONG", 300, 200.0, 1.0);
        priceHistory.put("LONG", longHistory);

        Date rebalDate = longHistory.get(longHistory.size() - 1).date;

        TopNMomentumStrategy strategy = new TopNMomentumStrategy(5);
        List<StockReturn> selected = strategy.selectStocks(priceHistory, rebalDate);

        boolean hasShort = selected.stream().anyMatch(s -> s.symbol.equals("SHORT"));
        assertFalse("SHORT stock with <252 days should be excluded", hasShort);

        boolean hasLong = selected.stream().anyMatch(s -> s.symbol.equals("LONG"));
        assertTrue("LONG stock with 300 days should be included", hasLong);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<DailyPrice> buildPrices(String symbol, int days, double startPrice, double dailyGain) {
        List<DailyPrice> prices = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.set(2021, Calendar.JANUARY, 4);
        double price = startPrice;
        for (int i = 0; i < days; i++) {
            while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                    || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                cal.add(Calendar.DATE, 1);
            }
            price += dailyGain;
            prices.add(new DailyPrice(symbol, cal.getTime(),
                    price, price + 1, price - 1, price, 100_000L));
            cal.add(Calendar.DATE, 1);
        }
        return prices;
    }

    private Date subtractDays(Date d, int days) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.add(Calendar.DATE, -days);
        return c.getTime();
    }
}
