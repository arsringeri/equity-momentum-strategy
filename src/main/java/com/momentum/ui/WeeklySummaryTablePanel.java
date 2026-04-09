package com.momentum.ui;

import com.momentum.model.WeeklySummary;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * JTable showing per-rebalance week: Date | Portfolio Value | Added | Removed | Weekly Return.
 */
public class WeeklySummaryTablePanel extends JPanel {

    private static final String[] COLUMNS = {
        "Week", "Date", "Portfolio Value (₹)", "Stocks Added", "Stocks Removed", "Weekly Return"
    };

    private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    public WeeklySummaryTablePanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Weekly Rebalance Summary",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12)));

        table.setFillsViewportHeight(true);
        table.setBackground(new Color(25, 25, 50));
        table.setForeground(Color.LIGHT_GRAY);
        table.setGridColor(new Color(50, 50, 90));
        table.setSelectionBackground(new Color(60, 60, 120));
        table.getTableHeader().setBackground(new Color(40, 40, 80));
        table.getTableHeader().setForeground(Color.WHITE);
        table.setRowHeight(20);

        // Color-code the weekly return column
        table.getColumnModel().getColumn(5).setCellRenderer(new ReturnCellRenderer());

        // Right-align portfolio value
        DefaultTableCellRenderer rightAlign = new DefaultTableCellRenderer();
        rightAlign.setHorizontalAlignment(JLabel.RIGHT);
        rightAlign.setBackground(new Color(25, 25, 50));
        rightAlign.setForeground(Color.LIGHT_GRAY);
        table.getColumnModel().getColumn(2).setCellRenderer(rightAlign);

        // Set preferred column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(130);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        table.getColumnModel().getColumn(4).setPreferredWidth(200);
        table.getColumnModel().getColumn(5).setPreferredWidth(90);

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /** Populates the table. Must be called on EDT. */
    public void update(List<WeeklySummary> summaries) {
        model.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(2);
        for (WeeklySummary ws : summaries) {
            String retStr = Double.isNaN(ws.weeklyReturn)
                    ? "—"
                    : String.format("%+.2f%%", ws.weeklyReturn * 100);
            model.addRow(new Object[]{
                ws.weekNumber,
                sdf.format(ws.date),
                nf.format(ws.portfolioValue),
                String.join(", ", ws.stocksAdded),
                String.join(", ", ws.stocksRemoved),
                retStr
            });
        }
    }

    public void clear() {
        model.setRowCount(0);
    }

    // -------------------------------------------------------------------------

    /** Green for positive returns, red for negative. */
    private static class ReturnCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            setHorizontalAlignment(JLabel.RIGHT);
            setBackground(new Color(25, 25, 50));
            if (value == null || "—".equals(value.toString())) {
                setForeground(Color.LIGHT_GRAY);
            } else {
                String s = value.toString().replace("%", "").replace("+", "").trim();
                try {
                    double v = Double.parseDouble(s);
                    setForeground(v >= 0 ? new Color(80, 220, 80) : new Color(220, 80, 80));
                } catch (NumberFormatException e) {
                    setForeground(Color.LIGHT_GRAY);
                }
            }
            return this;
        }
    }
}
