package com.momentum.ui;

import com.momentum.model.PortfolioSnapshot;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * JTable displaying Date | Portfolio Value for every trading day.
 */
public class DailyNavTablePanel extends JPanel {

    private static final String[] COLUMNS = {"Date", "Portfolio Value (₹)"};

    private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };

    private final JTable table = new JTable(model);

    public DailyNavTablePanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Daily NAV",
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

        // Right-align numbers
        DefaultTableCellRenderer rightAlign = new DefaultTableCellRenderer();
        rightAlign.setHorizontalAlignment(JLabel.RIGHT);
        rightAlign.setBackground(new Color(25, 25, 50));
        rightAlign.setForeground(Color.LIGHT_GRAY);
        table.getColumnModel().getColumn(1).setCellRenderer(rightAlign);

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /** Populates the table. Must be called on EDT. */
    public void update(List<PortfolioSnapshot> snapshots) {
        model.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(2);
        for (PortfolioSnapshot snap : snapshots) {
            model.addRow(new Object[]{
                sdf.format(snap.date),
                nf.format(snap.totalValue)
            });
        }
        if (model.getRowCount() > 0) {
            // Scroll to bottom (most recent)
            int lastRow = model.getRowCount() - 1;
            table.scrollRectToVisible(table.getCellRect(lastRow, 0, true));
        }
    }

    public void clear() {
        model.setRowCount(0);
    }
}
