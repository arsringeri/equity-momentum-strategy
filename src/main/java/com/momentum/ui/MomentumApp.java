package com.momentum.ui;

import com.momentum.config.MomentumConfig;
import com.momentum.data.KitePriceDataProvider;
import com.momentum.engine.BacktestEngine;
import com.momentum.model.BacktestResult;
import com.momentum.strategy.TopNMomentumStrategy;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Date;
import java.util.List;

/**
 * Main application window.
 *
 * Layout (BorderLayout):
 *   NORTH  - InputPanel   (date pickers, Run button, progress bar)
 *   CENTER - NavChartPanel (JFreeChart daily NAV)
 *   SOUTH  - JTabbedPane  (Daily NAV table | Weekly Summary table)
 *   EAST   - MetricsPanel (performance metrics)
 */
public class MomentumApp extends JFrame {

    private final MomentumConfig config;

    private final InputPanel               inputPanel;
    private final NavChartPanel            chartPanel;
    private final DailyNavTablePanel       dailyTable;
    private final WeeklySummaryTablePanel  weeklyTable;
    private final MetricsPanel             metricsPanel;

    public MomentumApp(MomentumConfig config) {
        super("Nifty 500 Momentum Backtest — Equity Strategy Platform");
        this.config = config;

        inputPanel   = new InputPanel();
        chartPanel   = new NavChartPanel();
        dailyTable   = new DailyNavTablePanel();
        weeklyTable  = new WeeklySummaryTablePanel();
        metricsPanel = new MetricsPanel();

        // Tabbed pane for the two tables (placed at SOUTH)
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(new Color(25, 25, 50));
        tabbedPane.setForeground(Color.LIGHT_GRAY);
        tabbedPane.addTab("Daily NAV",       dailyTable);
        tabbedPane.addTab("Weekly Summary",  weeklyTable);
        tabbedPane.setPreferredSize(new Dimension(0, 260));

        // Main content (center + south in a sub-panel)
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(new Color(20, 20, 40));
        center.add(chartPanel,  BorderLayout.CENTER);
        center.add(tabbedPane,  BorderLayout.SOUTH);

        getContentPane().setBackground(new Color(20, 20, 40));
        setLayout(new BorderLayout());
        add(inputPanel,   BorderLayout.NORTH);
        add(center,       BorderLayout.CENTER);
        add(metricsPanel, BorderLayout.EAST);

        inputPanel.getRunButton().addActionListener(this::onRunClicked);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 780);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Action handler
    // -------------------------------------------------------------------------

    private void onRunClicked(ActionEvent e) {
        Date fromDate = inputPanel.getFromDate();
        Date toDate   = inputPanel.getToDate();

        if (!fromDate.before(toDate)) {
            JOptionPane.showMessageDialog(this,
                    "\"From Date\" must be before \"To Date\".",
                    "Invalid Date Range", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validate api key
        if (config.getApiKey().equals("YOUR_API_KEY_HERE") || config.getApiKey().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please set kite.api_key in momentum.properties before running.",
                    "API Key Missing", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Clear previous results
        chartPanel.clear();
        dailyTable.clear();
        weeklyTable.clear();
        metricsPanel.reset();
        inputPanel.setRunning(true);
        inputPanel.setStatus("Initialising...");

        // Run backtest on background thread
        BacktestWorker worker = new BacktestWorker(fromDate, toDate);
        worker.execute();
    }

    // -------------------------------------------------------------------------
    // SwingWorker
    // -------------------------------------------------------------------------

    private class BacktestWorker extends SwingWorker<BacktestResult, String> {

        private final Date fromDate;
        private final Date toDate;

        BacktestWorker(Date fromDate, Date toDate) {
            this.fromDate = fromDate;
            this.toDate   = toDate;
        }

        @Override
        protected BacktestResult doInBackground() throws Exception {
            KitePriceDataProvider dataProvider = new KitePriceDataProvider(config);
            TopNMomentumStrategy  strategy     = new TopNMomentumStrategy(config.getTopN());
            BacktestEngine        engine       = new BacktestEngine(config, dataProvider, strategy);

            BacktestEngine.ProgressListener listener = (done, total, symbol) -> {
                int pct = (total > 0) ? (done * 100 / total) : 0;
                publish("Fetching " + symbol + " (" + done + "/" + total + ")");
                SwingUtilities.invokeLater(() ->
                        inputPanel.setProgress(done, total, done + "/" + total));
            };

            return engine.run(fromDate, toDate, listener);
        }

        @Override
        protected void process(List<String> chunks) {
            if (!chunks.isEmpty()) {
                inputPanel.setStatus(chunks.get(chunks.size() - 1));
            }
        }

        @Override
        protected void done() {
            inputPanel.setRunning(false);
            try {
                BacktestResult result = get();
                chartPanel .update(result.dailySnapshots);
                dailyTable .update(result.dailySnapshots);
                weeklyTable.update(result.weeklySummaries);
                metricsPanel.update(result);
                inputPanel.setStatus("Backtest complete. "
                        + result.dailySnapshots.size() + " days, "
                        + result.trades.size() + " trades.");
            } catch (Exception ex) {
                String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                inputPanel.setStatus("Error: " + msg);
                JOptionPane.showMessageDialog(MomentumApp.this,
                        "Backtest failed:\n" + msg,
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
