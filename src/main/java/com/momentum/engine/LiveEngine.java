package com.momentum.engine;

import com.momentum.config.MomentumConfig;
import com.momentum.strategy.MomentumStrategy;

import java.util.Date;

/**
 * PLACEHOLDER — Live Trading Engine.
 *
 * This class is a stub for future live execution via Zerodha Kite Connect.
 * It is intentionally left unimplemented.
 *
 * Future integration points:
 *  - Use KitePriceDataProvider for real-time quotes
 *  - Use KiteConnect.placeOrder() for executing BUY/SELL trades
 *  - Schedule weekly rebalancing every Monday at market open (09:15 IST)
 *  - Implement position sizing, slippage, and partial-fill handling
 *  - Add risk controls: max single-stock weight, circuit-breaker on drawdown
 */
public class LiveEngine {

    private final MomentumConfig   config;
    private final MomentumStrategy strategy;

    public LiveEngine(MomentumConfig config, MomentumStrategy strategy) {
        this.config   = config;
        this.strategy = strategy;
    }

    /**
     * TODO: Start the live trading loop.
     * - Connect to Kite tick feed
     * - On each Monday at market open, fetch current prices, run strategy, execute trades
     * - Track portfolio value in real-time
     */
    public void start(Date fromDate) {
        throw new UnsupportedOperationException(
            "Live trading mode is not yet implemented. "
            + "Run Simulation Mode from the UI instead.");
    }
}
