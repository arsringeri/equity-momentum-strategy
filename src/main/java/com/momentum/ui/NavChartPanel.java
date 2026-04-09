package com.momentum.ui;

import com.momentum.model.PortfolioSnapshot;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * JFreeChart line chart showing daily portfolio NAV over time.
 */
public class NavChartPanel extends JPanel {

    private final TimeSeries  series  = new TimeSeries("Portfolio NAV");
    private final ChartPanel  chartPanel;

    public NavChartPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Daily NAV",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12)));

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                null,                   // title
                "Date",                 // x-axis label
                "Portfolio Value (₹)",  // y-axis label
                dataset,
                false, true, false);

        // Style the chart
        chart.setBackgroundPaint(new Color(20, 20, 40));
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(new Color(30, 30, 60));
        plot.setDomainGridlinePaint(new Color(60, 60, 100));
        plot.setRangeGridlinePaint(new Color(60, 60, 100));

        // Date axis formatting
        DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
        dateAxis.setDateFormatOverride(new SimpleDateFormat("MMM-yy"));
        dateAxis.setLabelPaint(Color.LIGHT_GRAY);
        dateAxis.setTickLabelPaint(Color.LIGHT_GRAY);

        plot.getRangeAxis().setLabelPaint(Color.LIGHT_GRAY);
        plot.getRangeAxis().setTickLabelPaint(Color.LIGHT_GRAY);

        // Line renderer — no dots for large datasets
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, new Color(80, 200, 255));
        renderer.setSeriesStroke(0, new BasicStroke(1.8f));
        plot.setRenderer(renderer);

        chartPanel = new ChartPanel(chart);
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setPreferredSize(new Dimension(800, 320));
        add(chartPanel, BorderLayout.CENTER);
    }

    /** Populates the chart with daily NAV data. Must be called on EDT. */
    public void update(List<PortfolioSnapshot> snapshots) {
        series.clear();
        for (PortfolioSnapshot snap : snapshots) {
            try {
                series.addOrUpdate(new Day(snap.date), snap.totalValue);
            } catch (Exception ignored) {}
        }
    }

    public void clear() {
        series.clear();
    }
}
