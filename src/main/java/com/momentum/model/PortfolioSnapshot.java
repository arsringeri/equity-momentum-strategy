package com.momentum.model;

import java.util.Collections;
import java.util.Date;
import java.util.Map;

/**
 * Portfolio state at the close of a single calendar day.
 */
public class PortfolioSnapshot {
    public final Date                date;
    public final double              totalValue;
    /** symbol → number of shares held */
    public final Map<String, Double> holdings;

    public PortfolioSnapshot(Date date, double totalValue, Map<String, Double> holdings) {
        this.date       = date;
        this.totalValue = totalValue;
        this.holdings   = Collections.unmodifiableMap(holdings);
    }

    @Override
    public String toString() {
        return date + " NAV=" + String.format("%.2f", totalValue);
    }
}
