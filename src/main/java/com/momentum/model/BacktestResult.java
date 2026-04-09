package com.momentum.model;

import java.util.List;
import java.util.Map;

/**
 * Complete result of a backtest run.
 */
public class BacktestResult {
    public final List<PortfolioSnapshot> dailySnapshots;
    public final List<WeeklySummary>     weeklySummaries;
    public final List<Trade>             trades;
    public final Map<String, Double>     metrics;   // keys: totalReturn, cagr, maxDrawdown, tradeCount

    public BacktestResult(List<PortfolioSnapshot> dailySnapshots,
                          List<WeeklySummary>     weeklySummaries,
                          List<Trade>             trades,
                          Map<String, Double>     metrics) {
        this.dailySnapshots  = dailySnapshots;
        this.weeklySummaries = weeklySummaries;
        this.trades          = trades;
        this.metrics         = metrics;
    }

    @Override
    public String toString() {
        return "BacktestResult{snapshots=" + dailySnapshots.size()
                + ", weeks=" + weeklySummaries.size()
                + ", trades=" + trades.size()
                + ", metrics=" + metrics + "}";
    }
}
