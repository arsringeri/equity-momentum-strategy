package com.momentum.strategy;

import com.momentum.model.DailyPrice;
import com.momentum.model.StockReturn;

import java.util.*;
import java.util.logging.Logger;

/**
 * Selects the top-N stocks by 1-year price momentum.
 *
 * Signal definition:
 *   Return = (close on rebalanceDate) / (close ~252 trading days before rebalanceDate) - 1
 *
 * Eligibility:
 *   A stock is included in ranking only if it has at least MIN_TRADING_DAYS of
 *   price history ending on (or before) rebalanceDate.
 *
 * Equal weight:
 *   Each selected stock receives 1/topN of the portfolio (5% for topN=20).
 */
public class TopNMomentumStrategy implements MomentumStrategy {

    private static final Logger LOG = Logger.getLogger(TopNMomentumStrategy.class.getName());

    /** Minimum trading days of history required to compute the 1-year return. */
    private static final int MIN_TRADING_DAYS = 252;

    private final int topN;

    public TopNMomentumStrategy(int topN) {
        this.topN = topN;
    }

    @Override
    public List<StockReturn> selectStocks(Map<String, List<DailyPrice>> priceHistory,
                                          Date rebalanceDate) {
        List<StockReturn> ranked = new ArrayList<>();

        for (Map.Entry<String, List<DailyPrice>> entry : priceHistory.entrySet()) {
            String            symbol = entry.getKey();
            List<DailyPrice>  prices = entry.getValue();

            // Filter prices up to and including rebalanceDate, ascending
            List<DailyPrice> eligible = pricesUpTo(prices, rebalanceDate);

            if (eligible.size() < MIN_TRADING_DAYS) continue;

            double priceToday  = eligible.get(eligible.size() - 1).close;
            double price1YrAgo = eligible.get(eligible.size() - MIN_TRADING_DAYS).close;

            if (price1YrAgo <= 0 || priceToday <= 0) continue;

            double ret = (priceToday / price1YrAgo) - 1.0;
            ranked.add(new StockReturn(symbol, ret));
        }

        Collections.sort(ranked);  // descending by return (see StockReturn.compareTo)

        List<StockReturn> selected = ranked.subList(0, Math.min(topN, ranked.size()));
        LOG.fine("Rebalance " + rebalanceDate + ": ranked " + ranked.size()
                + " stocks, selected top " + selected.size());
        return new ArrayList<>(selected);
    }

    @Override
    public String getName() {
        return "Top-" + topN + " 1-Year Momentum";
    }

    /** Returns a sublist of prices whose date <= cutoff, in ascending order. */
    private List<DailyPrice> pricesUpTo(List<DailyPrice> prices, Date cutoff) {
        List<DailyPrice> result = new ArrayList<>();
        for (DailyPrice p : prices) {
            if (!p.date.after(cutoff)) result.add(p);
        }
        return result;
    }
}
