package com.momentum.engine;

import com.momentum.model.PortfolioSnapshot;

import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Computes standard performance metrics from a list of daily portfolio snapshots.
 */
public class PerformanceCalculator {

    private static final Logger LOG = Logger.getLogger(PerformanceCalculator.class.getName());

    /**
     * Computes metrics.
     * Keys: initialNAV, finalNAV, totalReturn, cagr, maxDrawdown, tradeCount
     */
    public static Map<String, Double> compute(List<PortfolioSnapshot> snapshots, int tradeCount) {
        Map<String, Double> m = new LinkedHashMap<>();

        if (snapshots == null || snapshots.isEmpty()) {
            LOG.warning("PerformanceCalculator: no snapshots provided — returning zero metrics");
            m.put("initialNAV",  0.0);
            m.put("finalNAV",    0.0);
            m.put("totalReturn", 0.0);
            m.put("cagr",        0.0);
            m.put("maxDrawdown", 0.0);
            m.put("tradeCount",  (double) tradeCount);
            return m;
        }

        double initialNAV = snapshots.get(0).totalValue;
        double finalNAV   = snapshots.get(snapshots.size() - 1).totalValue;

        // Total Return
        double totalReturn = (initialNAV > 0) ? (finalNAV - initialNAV) / initialNAV : 0.0;

        // CAGR
        long   msFirst = snapshots.get(0).date.getTime();
        long   msLast  = snapshots.get(snapshots.size() - 1).date.getTime();
        double years   = (msLast - msFirst) / (365.25 * 24 * 3600 * 1000.0);
        double cagr    = 0.0;
        if (years > 0 && initialNAV > 0) {
            cagr = Math.pow(finalNAV / initialNAV, 1.0 / years) - 1.0;
        }

        // Max Drawdown
        double peak        = Double.NEGATIVE_INFINITY;
        double maxDrawdown = 0.0;
        double peakValue   = initialNAV;
        double troughValue = initialNAV;

        for (PortfolioSnapshot snap : snapshots) {
            if (snap.totalValue > peak) {
                peak = snap.totalValue;
            }
            double dd = (peak > 0) ? (peak - snap.totalValue) / peak : 0.0;
            if (dd > maxDrawdown) {
                maxDrawdown = dd;
                peakValue   = peak;
                troughValue = snap.totalValue;
            }
        }

        m.put("initialNAV",  initialNAV);
        m.put("finalNAV",    finalNAV);
        m.put("totalReturn", totalReturn);
        m.put("cagr",        cagr);
        m.put("maxDrawdown", maxDrawdown);
        m.put("tradeCount",  (double) tradeCount);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String firstDate = sdf.format(snapshots.get(0).date);
        String lastDate  = sdf.format(snapshots.get(snapshots.size() - 1).date);

        LOG.info("=== Performance Metrics ===");
        LOG.info("  Period       : " + firstDate + " → " + lastDate
                + "  (" + String.format("%.2f", years) + " years)");
        LOG.info("  Days tracked : " + snapshots.size());
        LOG.info("  Initial NAV  : " + String.format("%,.2f", initialNAV));
        LOG.info("  Final NAV    : " + String.format("%,.2f", finalNAV));
        LOG.info("  Total Return : " + String.format("%+.2f%%", totalReturn * 100));
        LOG.info("  CAGR         : " + String.format("%+.2f%%", cagr * 100));
        LOG.info("  Max Drawdown : " + String.format("%.2f%%", maxDrawdown * 100)
                + "  (peak=" + String.format("%,.2f", peakValue)
                + "  trough=" + String.format("%,.2f", troughValue) + ")");
        LOG.info("  Total Trades : " + tradeCount);
        LOG.info("===========================");

        return m;
    }
}
