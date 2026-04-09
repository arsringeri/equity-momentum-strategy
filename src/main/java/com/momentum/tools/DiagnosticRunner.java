package com.momentum.tools;

import com.momentum.broker.KiteSessionManager;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Instrument;
import com.zerodhatech.models.Profile;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Standalone diagnostic — run this INSTEAD of Main to isolate the exact failure point.
 * Right-click → Run As → Java Application
 *
 * Tests in order:
 *   Step 1 — getProfile()              (token validity)
 *   Step 2 — getInstruments("NSE")     (catalogue + RELIANCE token lookup)
 *   Step 3 — getHistoricalData "day"   (RELIANCE, last 30 days)
 *   Step 4 — getHistoricalData "day"   (RELIANCE, full 2-year range like real backtest)
 *   Step 5 — getHistoricalData "day"   (TCS, last 30 days — second symbol cross-check)
 */
public class DiagnosticRunner {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception {
        System.out.println("=== Momentum Diagnostic Runner ===");
        System.out.println("Time: " + SDF.format(new Date()));
        System.out.println("CWD : " + System.getProperty("user.dir"));
        System.out.println();

        // ---------------------------------------------------------------
        // Step 1: Token validity
        // ---------------------------------------------------------------
        section("STEP 1: getProfile() — token validity check");
        KiteConnect kite = KiteSessionManager.getConnection();
        try {
            Profile p = kite.getProfile();
            ok("getProfile() succeeded");
            System.out.println("  userName      = " + p.userName);
            System.out.println("  userShortname = " + p.userShortname);
            System.out.println("  email         = " + p.email);
            System.out.println("  broker        = " + p.broker);
            System.out.println("  exchanges     = " + Arrays.toString(p.exchanges));
        } catch (com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException ke) {
            fail("getProfile() KiteException: code=" + ke.code + "  msg=" + ke.getMessage());
            System.out.println("  → Cannot proceed. Token is likely expired.");
            System.out.println("  → Run update-tokens.bat in C:\\bot first.");
            return;
        } catch (Throwable e) {
            fail("getProfile() FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("  → Cannot proceed. Token is likely expired.");
            System.out.println("  → Run update-tokens.bat in C:\\bot first.");
            return;
        }

        // ---------------------------------------------------------------
        // Step 2: NSE instrument catalogue
        // ---------------------------------------------------------------
        section("STEP 2: getInstruments(\"NSE\") — catalogue lookup");
        List<Instrument> instruments;
        try {
            instruments = kite.getInstruments("NSE");
            ok("getInstruments(NSE) returned " + instruments.size() + " instruments");
        } catch (com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException ke) {
            fail("getInstruments(NSE) KiteException: code=" + ke.code + "  msg=" + ke.getMessage());
            return;
        } catch (Throwable e) {
            fail("getInstruments(NSE) FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        // Find RELIANCE and TCS tokens
        long relianceToken = 0, tcsToken = 0;
        for (Instrument inst : instruments) {
            if ("RELIANCE".equals(inst.tradingsymbol))  relianceToken = inst.instrument_token;
            if ("TCS".equals(inst.tradingsymbol))       tcsToken      = inst.instrument_token;
        }

        if (relianceToken == 0) {
            fail("RELIANCE not found in NSE instrument catalogue!");
            System.out.println("  → All 549 symbols will likely also be missing.");
            System.out.println("  → Check: is the NSE equity segment available on your Kite account?");
        } else {
            ok("RELIANCE token = " + relianceToken);
        }
        if (tcsToken == 0) {
            warn("TCS not found in NSE instrument catalogue");
        } else {
            ok("TCS token = " + tcsToken);
        }

        if (relianceToken == 0) return;

        // ---------------------------------------------------------------
        // Step 3: Daily historical data — RELIANCE, last 30 days
        // ---------------------------------------------------------------
        section("STEP 3: getHistoricalData(\"day\") — RELIANCE, last 30 calendar days");
        Calendar cal = Calendar.getInstance();
        Date to30   = cal.getTime();
        cal.add(Calendar.DATE, -30);
        Date from30 = cal.getTime();
        fetchAndPrint(kite, relianceToken, "RELIANCE", from30, to30, "day");

        // ---------------------------------------------------------------
        // Step 4: Daily historical data — RELIANCE, full 2-year range
        // ---------------------------------------------------------------
        section("STEP 4: getHistoricalData(\"day\") — RELIANCE, full 2-year range (real backtest range)");
        Calendar cal2 = Calendar.getInstance();
        Date to2y   = cal2.getTime();
        cal2.add(Calendar.YEAR, -2);
        cal2.add(Calendar.DATE, -380);
        Date from2y = cal2.getTime();
        fetchAndPrint(kite, relianceToken, "RELIANCE", from2y, to2y, "day");

        // ---------------------------------------------------------------
        // Step 5: Cross-check with TCS
        // ---------------------------------------------------------------
        if (tcsToken != 0) {
            section("STEP 5: getHistoricalData(\"day\") — TCS, last 30 days");
            Calendar cal3 = Calendar.getInstance();
            Date to3   = cal3.getTime();
            cal3.add(Calendar.DATE, -30);
            Date from3 = cal3.getTime();
            fetchAndPrint(kite, tcsToken, "TCS", from3, to3, "day");
        }

        System.out.println();
        System.out.println("=== Diagnostic complete ===");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static void fetchAndPrint(KiteConnect kite, long token, String symbol,
                                       Date from, Date to, String interval) {
        System.out.printf("  Params: token=%-10d  from=%-12s  to=%-12s  interval=%s%n",
                token, fmt(from), fmt(to), interval);
        try {
            long t0 = System.currentTimeMillis();
            HistoricalData result = kite.getHistoricalData(
                    from, to, String.valueOf(token), interval, false, false);
            long elapsed = System.currentTimeMillis() - t0;

            if (result == null) {
                fail(symbol + ": result object is NULL  (" + elapsed + "ms)");
                return;
            }

            System.out.println("  Raw result fields:");
            System.out.println("    result.open         = " + result.open);
            System.out.println("    result.high         = " + result.high);
            System.out.println("    result.low          = " + result.low);
            System.out.println("    result.close        = " + result.close);
            System.out.println("    result.volume       = " + result.volume);
            System.out.println("    result.timeStamp    = " + result.timeStamp);
            System.out.println("    result.dataArrayList= "
                    + (result.dataArrayList == null ? "NULL"
                       : result.dataArrayList.size() + " entries"));

            if (result.dataArrayList == null || result.dataArrayList.isEmpty()) {
                fail(symbol + ": dataArrayList is empty/null  (" + elapsed + "ms)");
                System.out.println("  → BUT raw fields above may show if data came in a different structure");
                return;
            }

            int n = result.dataArrayList.size();
            HistoricalData first = result.dataArrayList.get(0);
            HistoricalData last  = result.dataArrayList.get(n - 1);

            ok(symbol + ": " + n + " candles returned  (" + elapsed + "ms)");
            System.out.printf("    First: ts=%-25s  O=%.2f  H=%.2f  L=%.2f  C=%.2f  V=%d%n",
                    first.timeStamp, first.open, first.high, first.low, first.close, first.volume);
            System.out.printf("    Last:  ts=%-25s  O=%.2f  H=%.2f  L=%.2f  C=%.2f  V=%d%n",
                    last.timeStamp,  last.open,  last.high,  last.low,  last.close,  last.volume);

        } catch (com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException ke) {
            fail(symbol + " KiteException: code=" + ke.code + "  msg=" + ke.getMessage());
        } catch (Exception e) {
            fail(symbol + " Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("------------------------------------------------------------");
        System.out.println(title);
        System.out.println("------------------------------------------------------------");
    }

    private static void ok(String msg)   { System.out.println("  [OK  ] " + msg); }
    private static void fail(String msg) { System.out.println("  [FAIL] " + msg); }
    private static void warn(String msg) { System.out.println("  [WARN] " + msg); }

    private static String fmt(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd").format(d);
    }
}
