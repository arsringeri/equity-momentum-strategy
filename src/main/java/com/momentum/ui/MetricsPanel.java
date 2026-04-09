package com.momentum.ui;

import com.momentum.model.BacktestResult;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Map;

/**
 * Displays four key performance metrics in a vertical panel on the right side of the UI.
 */
public class MetricsPanel extends JPanel {

    private final JLabel lblTotalReturn  = metric("—");
    private final JLabel lblCAGR         = metric("—");
    private final JLabel lblMaxDrawdown  = metric("—");
    private final JLabel lblTradeCount   = metric("—");
    private final JLabel lblInitialNAV   = metric("—");
    private final JLabel lblFinalNAV     = metric("—");

    public MetricsPanel() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Performance Metrics",
                TitledBorder.CENTER, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12)));
        setBackground(new Color(30, 30, 60));
        setPreferredSize(new Dimension(180, 0));

        GridBagConstraints c = new GridBagConstraints();
        c.insets    = new Insets(6, 10, 2, 10);
        c.gridx     = 0;
        c.fill      = GridBagConstraints.HORIZONTAL;
        c.weightx   = 1.0;
        c.anchor    = GridBagConstraints.NORTH;

        int row = 0;
        c.gridy = row++; add(heading("Initial Capital"),  c);
        c.gridy = row++; add(lblInitialNAV,               c);
        c.gridy = row++; add(sep(), c);
        c.gridy = row++; add(heading("Final NAV"),        c);
        c.gridy = row++; add(lblFinalNAV,                 c);
        c.gridy = row++; add(sep(), c);
        c.gridy = row++; add(heading("Total Return"),     c);
        c.gridy = row++; add(lblTotalReturn,              c);
        c.gridy = row++; add(sep(), c);
        c.gridy = row++; add(heading("CAGR"),             c);
        c.gridy = row++; add(lblCAGR,                     c);
        c.gridy = row++; add(sep(), c);
        c.gridy = row++; add(heading("Max Drawdown"),     c);
        c.gridy = row++; add(lblMaxDrawdown,              c);
        c.gridy = row++; add(sep(), c);
        c.gridy = row++; add(heading("Total Trades"),     c);
        c.gridy = row++;
        c.weighty = 1.0;
        add(lblTradeCount, c);
    }

    public void update(BacktestResult result) {
        Map<String, Double> m = result.metrics;
        double totalReturn = m.getOrDefault("totalReturn", 0.0);
        double cagr        = m.getOrDefault("cagr", 0.0);
        double maxDD       = m.getOrDefault("maxDrawdown", 0.0);
        double trades      = m.getOrDefault("tradeCount", 0.0);
        double initial     = m.getOrDefault("initialNAV", 0.0);
        double finalV      = m.getOrDefault("finalNAV", 0.0);

        lblInitialNAV .setText(String.format("₹ %,.0f", initial));
        lblFinalNAV   .setText(String.format("₹ %,.0f", finalV));
        lblTotalReturn.setText(String.format("%+.2f%%", totalReturn * 100));
        lblCAGR       .setText(String.format("%+.2f%%", cagr        * 100));
        lblMaxDrawdown.setText(String.format("%.2f%%",  maxDD       * 100));
        lblTradeCount .setText(String.format("%,.0f",   trades));

        Color returnColor = totalReturn >= 0 ? new Color(80, 220, 80) : new Color(220, 80, 80);
        lblTotalReturn.setForeground(returnColor);
        lblCAGR.setForeground(cagr >= 0 ? new Color(80, 220, 80) : new Color(220, 80, 80));
    }

    public void reset() {
        lblInitialNAV .setText("—");
        lblFinalNAV   .setText("—");
        lblTotalReturn.setText("—");
        lblCAGR       .setText("—");
        lblMaxDrawdown.setText("—");
        lblTradeCount .setText("—");
    }

    // -------------------------------------------------------------------------

    private JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(new Color(160, 160, 200));
        return l;
    }

    private JLabel metric(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 16));
        l.setForeground(Color.WHITE);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
    }

    private JSeparator sep() {
        JSeparator s = new JSeparator();
        s.setForeground(new Color(80, 80, 120));
        return s;
    }
}
