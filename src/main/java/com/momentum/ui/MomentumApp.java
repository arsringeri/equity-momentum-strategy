package com.momentum.ui;

import com.momentum.config.MomentumConfig;
import com.momentum.data.KitePriceDataProvider;
import com.momentum.engine.BacktestEngine;
import com.momentum.model.BacktestResult;
import com.momentum.strategy.TopNMomentumStrategy;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Main application window.
 *
 * Layout (BorderLayout):
 *   NORTH  — InputPanel   (date pickers, Run button, progress bar)
 *   CENTER — NavChartPanel (JFreeChart daily NAV)
 *   SOUTH  — JTabbedPane  (Daily NAV table | Weekly Summary table)
 *   EAST   — MetricsPanel (performance metrics)
 */
public class MomentumApp extends JFrame {

    private static final Logger LOG = Logger.getLogger(MomentumApp.class.getName());

    private final MomentumConfig           config;
    private final InputPanel               inputPanel;
    private final NavChartPanel            chartPanel;
    private final DailyNavTablePanel       dailyTable;
    private final WeeklySummaryTablePanel  weeklyTable;
    private final MetricsPanel             metricsPanel;

    public MomentumApp(MomentumConfig config) {
        super("Nifty 500 Momentum Backtest — Equity Strategy Platform");
        this.config = config;
        LOG.info("MomentumApp UI initialising");

        inputPanel   = new InputPanel();
        chartPanel   = new NavChartPanel();
        dailyTable   = new DailyNavTablePanel();
        weeklyTable  = new WeeklySummaryTablePanel();
        metricsPanel = new MetricsPanel();

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(new Color(25, 25, 50));
        tabbedPane.setForeground(Color.LIGHT_GRAY);
        tabbedPane.addTab("Daily NAV",      dailyTable);
        tabbedPane.addTab("Weekly Summary", weeklyTable);
        tabbedPane.setPreferredSize(new Dimension(0, 260));

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(new Color(20, 20, 40));
        center.add(chartPanel, BorderLayout.CENTER);
        center.add(tabbedPane, BorderLayout.SOUTH);

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

        LOG.info("MomentumApp UI ready");
    }

    // -------------------------------------------------------------------------
    // Action handler
    // -------------------------------------------------------------------------

    private void onRunClicked(ActionEvent e) {
        Date fromDate = inputPanel.getFromDate();
        Date toDate   = inputPanel.getToDate();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        LOG.info("Run button clicked — from=" + sdf.format(fromDate)
                + "  to=" + sdf.format(toDate));

        if (!fromDate.before(toDate)) {
            LOG.warning("Invalid date range: from=" + sdf.format(fromDate)
                    + " is not before to=" + sdf.format(toDate));
            JOptionPane.showMessageDialog(this,
                    "\"From Date\" must be before \"To Date\".",
                    "Invalid Date Range", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Clear previous results
        chartPanel.clear();
        dailyTable.clear();
        weeklyTable.clear();
        metricsPanel.reset();
        inputPanel.setRunning(true);
        inputPanel.setStatus("Initialising...");

        LOG.info("Launching BacktestWorker on background thread");
        new BacktestWorker(fromDate, toDate).execute();
    }

    // -------------------------------------------------------------------------
    // SwingWorker
    // -------------------------------------------------------------------------

    private class BacktestWorker extends SwingWorker<BacktestResult, String> {

        private final Date fromDate;
        private final Date toDate;
        private final long startTime = System.currentTimeMillis();

        BacktestWorker(Date fromDate, Date toDate) {
            this.fromDate = fromDate;
            this.toDate   = toDate;
        }

        @Override
        protected BacktestResult doInBackground() throws Exception {
            LOG.info("BacktestWorker.doInBackground() — starting");
            KitePriceDataProvider dataProvider = new KitePriceDataProvider(config);
            TopNMomentumStrategy  strategy     = new TopNMomentumStrategy(config.getTopN());
            BacktestEngine        engine       = new BacktestEngine(config, dataProvider, strategy);

            BacktestEngine.ProgressListener listener = (done, total, symbol) -> {
                String msg = "Fetching " + symbol + " (" + done + "/" + total + ")";
                publish(msg);
                SwingUtilities.invokeLater(() ->
                        inputPanel.setProgress(done, total, done + "/" + total));
            };

            return engine.run(fromDate, toDate, listener);
        }

        @Override
        protected void process(List<String> chunks) {
            if (!chunks.isEmpty()) {
                String last = chunks.get(chunks.size() - 1);
                inputPanel.setStatus(last);
                LOG.fine("Progress: " + last);
            }
        }

        @Override
        protected void done() {
            long elapsed = System.currentTimeMillis() - startTime;
            inputPanel.setRunning(false);
            try {
                BacktestResult result = get();
                LOG.info("BacktestWorker.done() — success"
                        + "  elapsed=" + elapsed + "ms"
                        + "  days=" + result.dailySnapshots.size()
                        + "  weeks=" + result.weeklySummaries.size()
                        + "  trades=" + result.trades.size());

                chartPanel .update(result.dailySnapshots);
                dailyTable .update(result.dailySnapshots);
                weeklyTable.update(result.weeklySummaries);
                metricsPanel.update(result);

                String summary = "Done in " + (elapsed / 1000) + "s — "
                        + result.dailySnapshots.size() + " days, "
                        + result.trades.size() + " trades.";
                inputPanel.setStatus(summary);
                LOG.info(summary);

            } catch (Exception ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                LOG.severe("BacktestWorker FAILED after " + elapsed + "ms: " + cause.getMessage());
                inputPanel.setStatus("Error: " + cause.getMessage());
                JOptionPane.showMessageDialog(MomentumApp.this,
                        "Backtest failed:\n" + cause.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
