package com.momentum.strategy;

import com.momentum.model.DailyPrice;
import com.momentum.model.StockReturn;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Pluggable strategy interface for stock selection.
 * Implement this to swap in different ranking/filtering algorithms
 * without changing the backtest engine.
 */
public interface MomentumStrategy {

    /**
     * Select the stocks to hold starting from {@code rebalanceDate}.
     *
     * @param priceHistory map of symbol → full price history (sorted ascending by date)
     * @param rebalanceDate the Monday on which rebalancing occurs
     * @return ordered list of selected stocks with their return values,
     *         highest return first; strategy decides how many to return
     */
    List<StockReturn> selectStocks(Map<String, List<DailyPrice>> priceHistory, Date rebalanceDate);

    /** Human-readable name for display/logging. */
    String getName();
}
