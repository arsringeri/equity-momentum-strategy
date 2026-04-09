package com.momentum.model;

import java.util.Date;
import java.util.List;

/**
 * Summary of portfolio state and changes on each rebalance date (Monday).
 */
public class WeeklySummary {
    public final int        weekNumber;
    public final Date       date;
    public final double     portfolioValue;
    public final double     weeklyReturn;       // fraction, NaN for first week
    public final List<String> stocksAdded;
    public final List<String> stocksRemoved;

    public WeeklySummary(int weekNumber, Date date, double portfolioValue,
                         double weeklyReturn,
                         List<String> stocksAdded, List<String> stocksRemoved) {
        this.weekNumber     = weekNumber;
        this.date           = date;
        this.portfolioValue = portfolioValue;
        this.weeklyReturn   = weeklyReturn;
        this.stocksAdded    = stocksAdded;
        this.stocksRemoved  = stocksRemoved;
    }
}
