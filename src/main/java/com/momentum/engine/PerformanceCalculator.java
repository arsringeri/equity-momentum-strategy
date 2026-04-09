package com.momentum.engine;

import com.momentum.model.PortfolioSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes standard performance metrics from a list of daily portfolio snapshots.
 */
public class PerformanceCalculator {

    /**
     * Computes metrics and returns them as a named map.
     * Keys: totalReturn, cagr, maxDrawdown, tradeCount, initialNAV, finalNAV
     *
     * @param snapshots  daily snapshots, ascending by date
     * @param tradeCount total number of BUY+SELL trades executed
     */
    public static Map<String, Double> compute(List<PortfolioSnapshot> snapshots, int tradeCount) {
        Map<String, Double> m = new LinkedHashMap<>();
        if (snapshots == null || snapshots.isEmpty()) {
            m.put("totalReturn", 0.0);
            m.put("cagr", 0.0);
            m.put("maxDrawdown", 0.0);
            m.put("tradeCount", (double) tradeCount);
            return m;
        }

        double initialNAV = snapshots.get(0).totalValue;
        double finalNAV   = snapshots.get(snapshots.size() - 1).totalValue;

        // Total Return
        double totalReturn = (initialNAV > 0) ? (finalNAV - initialNAV) / initialNAV : 0.0;

        // CAGR: (finalNAV / initialNAV) ^ (1 / years) - 1
        long   msFirst = snapshots.get(0).date.getTime();
        long   msLast  = snapshots.get(snapshots.size() - 1).date.getTime();
        double years   = (msLast - msFirst) / (365.25 * 24 * 3600 * 1000.0);
        double cagr    = 0.0;
        if (years > 0 && initialNAV > 0) {
            cagr = Math.pow(finalNAV / initialNAV, 1.0 / years) - 1.0;
        }

        // Max Drawdown: max((peak - trough) / peak) over all windows
        double peak        = Double.NEGATIVE_INFINITY;
        double maxDrawdown = 0.0;
        for (PortfolioSnapshot snap : snapshots) {
            if (snap.totalValue > peak) peak = snap.totalValue;
            double dd = (peak > 0) ? (peak - snap.totalValue) / peak : 0.0;
            if (dd > maxDrawdown) maxDrawdown = dd;
        }

        m.put("initialNAV",   initialNAV);
        m.put("finalNAV",     finalNAV);
        m.put("totalReturn",  totalReturn);
        m.put("cagr",         cagr);
        m.put("maxDrawdown",  maxDrawdown);
        m.put("tradeCount",   (double) tradeCount);
        return m;
    }
}
