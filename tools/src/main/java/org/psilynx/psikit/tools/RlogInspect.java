package org.psilynx.psikit.tools;

import org.psilynx.psikit.core.LogTable;
import org.psilynx.psikit.core.rlog.RLOGReplay;

import java.util.Map;
import java.util.TreeMap;

/**
 * Desktop CLI that inspects an RLOG file.
 *
 * Prints:
 * - RealMetadata keys/values
 * - Mirrored build info keys under RealOutputs/PsiKit/BuildInfo (if present)
 *
 * Usage:
 *   gradlew :tools:run --args="inspect C:/path/to/log.rlog"
 */
public final class RlogInspect {

    private RlogInspect() {}

    public static void main(String[] args) {
        String logPath = (args != null && args.length > 0 && args[0] != null && !args[0].isBlank())
            ? args[0]
            : RLOGReplay.promptForPath();

        RLOGReplay replay = new RLOGReplay(logPath);
        replay.start();
        try {
            LogTable entry = replay.getEntry();
            if (entry == null) {
                System.err.println("No entries found (empty log?): " + logPath);
                System.exit(1);
                return;
            }

            System.out.println("Log: " + logPath);
            System.out.println("First timestamp: " + entry.getTimestamp());

            dumpSection("RealMetadata", entry.getSubtable("RealMetadata"));
            dumpSection("ReplayMetadata", entry.getSubtable("ReplayMetadata"));

            LogTable realOutputs = entry.getSubtable("RealOutputs");
            LogTable buildInfo = realOutputs.getSubtable("PsiKit").getSubtable("BuildInfo");
            dumpSection("RealOutputs/PsiKit/BuildInfo", buildInfo);
        } finally {
            replay.end();
        }
    }

    private static void dumpSection(String title, LogTable table) {
        Map<String, LogTable.LogValue> values;
        try {
            values = table.getAll(true);
        } catch (Throwable t) {
            return;
        }

        if (values == null || values.isEmpty()) {
            return;
        }

        System.out.println();
        System.out.println("== " + title + " ==");

        // Sort by key for stable output.
        Map<String, LogTable.LogValue> sorted = new TreeMap<>(values);
        for (Map.Entry<String, LogTable.LogValue> e : sorted.entrySet()) {
            String key = e.getKey();
            LogTable.LogValue v = e.getValue();
            if (key == null || v == null) continue;
            System.out.println(key + " = " + formatValue(v));
        }
    }

    private static String formatValue(LogTable.LogValue v) {
        try {
            Object raw = v.getObject(null);
            if (raw == null) return "";
            if (raw instanceof byte[]) return "<raw " + ((byte[]) raw).length + " bytes>";
            if (raw instanceof boolean[]) return "<bool[" + ((boolean[]) raw).length + "]>";
            if (raw instanceof long[]) return "<long[" + ((long[]) raw).length + "]>";
            if (raw instanceof double[]) return "<double[" + ((double[]) raw).length + "]>";
            if (raw instanceof String[]) return "<string[" + ((String[]) raw).length + "]>";
            return String.valueOf(raw);
        } catch (Throwable t) {
            return "<unprintable>";
        }
    }
}
