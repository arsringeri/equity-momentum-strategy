package com.momentum.model;

import java.util.Date;

/**
 * Represents a single buy or sell executed during rebalancing.
 */
public class Trade {
    public enum Direction { BUY, SELL }

    public final Date      date;
    public final String    symbol;
    public final Direction direction;
    public final double    qty;
    public final double    price;

    public Trade(Date date, String symbol, Direction direction, double qty, double price) {
        this.date      = date;
        this.symbol    = symbol;
        this.direction = direction;
        this.qty       = qty;
        this.price     = price;
    }

    public double value() {
        return qty * price;
    }

    @Override
    public String toString() {
        return direction + " " + symbol + " qty=" + String.format("%.2f", qty)
                + " @ " + String.format("%.2f", price) + " on " + date;
    }
}
