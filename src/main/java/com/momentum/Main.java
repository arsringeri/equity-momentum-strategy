package com.momentum;

import com.momentum.config.MomentumConfig;
import com.momentum.ui.MomentumApp;

import javax.swing.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

/**
 * Entry point for the Nifty 500 Momentum Backtest Platform.
 *
 * Usage:
 *   java -jar dist/momentum-1.0.0-fat.jar [path/to/momentum.properties]
 */
public class Main {

    private static final Logger LOG = Logger.getLogger("com.momentum");

    public static void main(String[] args) {
        configureLogging();

        String configPath = (args.length > 0) ? args[0] : "momentum.properties";
        LOG.info("==========================================================");
        LOG.info("  Nifty 500 Momentum Backtest Platform — starting up");
        LOG.info("  Time     : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        LOG.info("  Config   : " + new File(configPath).getAbsolutePath());
        LOG.info("  Java     : " + System.getProperty("java.version"));
        LOG.info("  CWD      : " + System.getProperty("user.dir"));
        LOG.info("==========================================================");

        MomentumConfig config = new MomentumConfig(configPath);
        config.logAll();    // dump all resolved config values

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            UIManager.put("TabbedPane.background", new java.awt.Color(25, 25, 50));
            UIManager.put("TabbedPane.foreground", java.awt.Color.LIGHT_GRAY);
            UIManager.put("TabbedPane.selected",   new java.awt.Color(40, 40, 80));

            LOG.info("Launching Swing UI on EDT");
            new MomentumApp(config);
        });
    }

    /** Configures java.util.logging with a compact single-line console format. */
    private static void configureLogging() {
        // Remove default handlers on root logger
        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) root.removeHandler(h);

        ConsoleHandler console = new ConsoleHandler();
        console.setLevel(Level.ALL);
        console.setFormatter(new Formatter() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            @Override
            public String format(LogRecord r) {
                String level = r.getLevel().getName();
                // Shorten level names for readability
                if (level.equals("WARNING")) level = "WARN ";
                else if (level.equals("SEVERE")) level = "ERROR";
                else if (level.equals("INFO"))   level = "INFO ";
                else if (level.equals("FINE"))   level = "FINE ";
                // Shorten logger name: com.momentum.engine.BacktestEngine → engine.BacktestEngine
                String logger = r.getLoggerName();
                if (logger != null && logger.startsWith("com.momentum.")) {
                    logger = logger.substring("com.momentum.".length());
                }
                String msg = formatMessage(r);
                if (r.getThrown() != null) {
                    msg += "\n  Caused by: " + r.getThrown();
                }
                return String.format("[%s] %s  %-40s  %s%n",
                        sdf.format(new Date(r.getMillis())), level, logger, msg);
            }
        });
        root.addHandler(console);
        root.setLevel(Level.INFO);

        // Allow FINE level from com.momentum for strategy details
        Logger momentumLog = Logger.getLogger("com.momentum");
        momentumLog.setLevel(Level.FINE);
    }
}
