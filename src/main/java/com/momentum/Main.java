package com.momentum;

import com.momentum.config.MomentumConfig;
import com.momentum.ui.MomentumApp;

import javax.swing.*;

/**
 * Entry point for the Nifty 500 Momentum Backtest Platform.
 *
 * Usage:
 *   java -jar dist/momentum-1.0.0-fat.jar [path/to/momentum.properties]
 *
 * If no argument is given, looks for momentum.properties in the working directory.
 */
public class Main {

    public static void main(String[] args) {
        String configPath = (args.length > 0) ? args[0] : "momentum.properties";
        MomentumConfig config = new MomentumConfig(configPath);

        // Launch Swing UI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            // Set UI colours for dark theme on all L&F
            UIManager.put("TabbedPane.background",  new java.awt.Color(25, 25, 50));
            UIManager.put("TabbedPane.foreground",  java.awt.Color.LIGHT_GRAY);
            UIManager.put("TabbedPane.selected",    new java.awt.Color(40, 40, 80));

            new MomentumApp(config);
        });
    }
}
