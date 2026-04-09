package com.momentum.strategy;

import com.momentum.model.DailyPrice;
import com.momentum.model.StockReturn;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * Selects the top-N stocks by 1-year price momentum.
 *
 * Signal: Return = (close on rebalanceDate) / (close ~252 trading days before) - 1
 * Eligibility: stock must have >= MIN_TRADING_DAYS of history before rebalanceDate.
 */
public class TopNMomentumStrategy implements MomentumStrategy {

    private static final Logger LOG = Logger.getLogger(TopNMomentumStrategy.class.getName());
    private static final int    MIN_TRADING_DAYS = 252;

    private final int topN;

    public TopNMomentumStrategy(int topN) {
        this.topN = topN;
        LOG.info("TopNMomentumStrategy created — topN=" + topN
                + "  minTradingDays=" + MIN_TRADING_DAYS);
    }

    @Override
    public List<StockReturn> selectStocks(Map<String, List<DailyPrice>> priceHistory,
                                          Date rebalanceDate) {
        String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(rebalanceDate);
        LOG.fine("--- selectStocks() rebalanceDate=" + dateStr
                + "  universe=" + priceHistory.size() + " symbols ---");

        int totalSymbols      = priceHistory.size();
        int excludedShortHist = 0;
        int excludedBadPrice  = 0;
        List<StockReturn> ranked = new ArrayList<>();

        for (Map.Entry<String, List<DailyPrice>> entry : priceHistory.entrySet()) {
            String           symbol = entry.getKey();
            List<DailyPrice> prices = entry.getValue();

            List<DailyPrice> eligible = pricesUpTo(prices, rebalanceDate);

            if (eligible.size() < MIN_TRADING_DAYS) {
                excludedShortHist++;
                LOG.fine("  EXCLUDED (short history) " + symbol
                        + " — only " + eligible.size() + " days (need " + MIN_TRADING_DAYS + ")");
                continue;
            }

            double priceToday  = eligible.get(eligible.size() - 1).close;
            double price1YrAgo = eligible.get(eligible.size() - MIN_TRADING_DAYS).close;

            if (price1YrAgo <= 0 || priceToday <= 0) {
                excludedBadPrice++;
                LOG.warning("  EXCLUDED (bad price) " + symbol
                        + " — priceToday=" + priceToday + "  price1YrAgo=" + price1YrAgo);
                continue;
            }

            double ret = (priceToday / price1YrAgo) - 1.0;
            ranked.add(new StockReturn(symbol, ret));
        }

        Collections.sort(ranked);  // descending by return

        List<StockReturn> selected = new ArrayList<>(ranked.subList(0, Math.min(topN, ranked.size())));

        LOG.info("selectStocks [" + dateStr + "]:"
                + "  total=" + totalSymbols
                + "  eligible=" + ranked.size()
                + "  excludedShortHist=" + excludedShortHist
                + "  excludedBadPrice=" + excludedBadPrice
                + "  selected=" + selected.size());

        if (!selected.isEmpty()) {
            LOG.fine("  Top stock: " + selected.get(0).symbol
                    + " return=" + String.format("%.2f%%", selected.get(0).oneYearReturn * 100));
            LOG.fine("  Last selected: " + selected.get(selected.size() - 1).symbol
                    + " return=" + String.format("%.2f%%",
                        selected.get(selected.size() - 1).oneYearReturn * 100));
            if (ranked.size() > selected.size()) {
                LOG.fine("  First excluded: " + ranked.get(selected.size()).symbol
                        + " return=" + String.format("%.2f%%",
                            ranked.get(selected.size()).oneYearReturn * 100));
            }
        } else {
            LOG.warning("selectStocks [" + dateStr + "]: NO stocks selected — check data coverage");
        }

        return selected;
    }

    @Override
    public String getName() {
        return "Top-" + topN + " 1-Year Momentum";
    }

    private List<DailyPrice> pricesUpTo(List<DailyPrice> prices, Date cutoff) {
        List<DailyPrice> result = new ArrayList<>();
        for (DailyPrice p : prices) {
            if (!p.date.after(cutoff)) result.add(p);
        }
        return result;
    }
}
