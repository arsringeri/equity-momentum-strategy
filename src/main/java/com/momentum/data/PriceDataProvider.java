package com.momentum.data;

import com.momentum.model.DailyPrice;

import java.util.Date;
import java.util.List;

/**
 * Abstraction over a source of daily OHLCV price data.
 * Swap implementations to change data sources without touching business logic.
 */
public interface PriceDataProvider {

    /**
     * Returns daily OHLCV rows for {@code symbol} between {@code from} and {@code to} inclusive,
     * sorted ascending by date.
     * Returns an empty list (never null) if no data is available.
     *
     * @param symbol NSE trading symbol, e.g. "RELIANCE"
     * @param from   start date (inclusive)
     * @param to     end date (inclusive)
     */
    List<DailyPrice> getDailyPrices(String symbol, Date from, Date to) throws Exception;

    /**
     * Called once before bulk data fetching begins.
     * Implementations may use this to warm up connections or load instrument catalogues.
     */
    void init() throws Exception;

    /**
     * Called after all data fetching is complete.
     * Implementations may release resources here.
     */
    void close();
}
