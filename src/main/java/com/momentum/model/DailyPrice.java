package com.momentum.model;

import java.util.Date;

/**
 * One row of daily OHLCV data for a single stock.
 */
public class DailyPrice {
    public final String symbol;
    public final Date   date;
    public final double open;
    public final double high;
    public final double low;
    public final double close;
    public final long   volume;

    public DailyPrice(String symbol, Date date,
                      double open, double high, double low, double close, long volume) {
        this.symbol = symbol;
        this.date   = date;
        this.open   = open;
        this.high   = high;
        this.low    = low;
        this.close  = close;
        this.volume = volume;
    }

    @Override
    public String toString() {
        return symbol + "@" + date + " O=" + open + " H=" + high + " L=" + low + " C=" + close;
    }
}
