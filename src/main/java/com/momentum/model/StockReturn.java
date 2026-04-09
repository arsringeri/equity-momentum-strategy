package com.momentum.model;

/**
 * Holds the computed 1-year momentum return for a stock on a given rebalance date.
 */
public class StockReturn implements Comparable<StockReturn> {
    public final String symbol;
    public final double oneYearReturn;   // (price_today / price_1yr_ago) - 1

    public StockReturn(String symbol, double oneYearReturn) {
        this.symbol        = symbol;
        this.oneYearReturn = oneYearReturn;
    }

    /** Descending sort (highest return first). */
    @Override
    public int compareTo(StockReturn other) {
        return Double.compare(other.oneYearReturn, this.oneYearReturn);
    }

    @Override
    public String toString() {
        return symbol + " (" + String.format("%.2f%%", oneYearReturn * 100) + ")";
    }
}
