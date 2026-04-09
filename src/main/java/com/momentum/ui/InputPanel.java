package com.momentum.ui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Calendar;
import java.util.Date;

/**
 * Top panel: From Date, To Date, Run button, progress bar, and status label.
 */
public class InputPanel extends JPanel {

    private final JSpinner  fromSpinner;
    private final JSpinner  toSpinner;
    private final JButton   runButton  = new JButton("Run Backtest");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel    statusLabel = new JLabel(" ");

    public InputPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 12, 8));
        setBackground(new Color(20, 20, 45));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Simulation Parameters",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12)));

        // Default: from = 3 years ago, to = today
        Calendar cal = Calendar.getInstance();
        Date toDefault   = cal.getTime();
        cal.add(Calendar.YEAR, -3);
        Date fromDefault = cal.getTime();

        SpinnerDateModel fromModel = new SpinnerDateModel(fromDefault, null, null, Calendar.DAY_OF_MONTH);
        SpinnerDateModel toModel   = new SpinnerDateModel(toDefault,   null, null, Calendar.DAY_OF_MONTH);

        fromSpinner = new JSpinner(fromModel);
        toSpinner   = new JSpinner(toModel);

        JSpinner.DateEditor fromEditor = new JSpinner.DateEditor(fromSpinner, "yyyy-MM-dd");
        JSpinner.DateEditor toEditor   = new JSpinner.DateEditor(toSpinner,   "yyyy-MM-dd");
        fromSpinner.setEditor(fromEditor);
        toSpinner.setEditor(toEditor);
        fromSpinner.setPreferredSize(new Dimension(110, 26));
        toSpinner.setPreferredSize(new Dimension(110, 26));

        runButton.setBackground(new Color(0, 120, 210));
        runButton.setForeground(Color.WHITE);
        runButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        runButton.setFocusPainted(false);

        progressBar.setStringPainted(true);
        progressBar.setString("Idle");
        progressBar.setPreferredSize(new Dimension(200, 22));

        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        add(label("From Date:"));
        add(fromSpinner);
        add(label("To Date:"));
        add(toSpinner);
        add(runButton);
        add(Box.createHorizontalStrut(16));
        add(progressBar);
        add(statusLabel);
    }

    // -------------------------------------------------------------------------
    // Public accessors
    // -------------------------------------------------------------------------

    public Date getFromDate() {
        return (Date) fromSpinner.getValue();
    }

    public Date getToDate() {
        return (Date) toSpinner.getValue();
    }

    public JButton getRunButton() {
        return runButton;
    }

    public void setProgress(int value, int max, String text) {
        progressBar.setMaximum(max);
        progressBar.setValue(value);
        progressBar.setString(text);
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    public void setRunning(boolean running) {
        runButton.setEnabled(!running);
        if (!running) {
            progressBar.setValue(0);
            progressBar.setString("Done");
        }
    }

    // -------------------------------------------------------------------------

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.LIGHT_GRAY);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return l;
    }
}
