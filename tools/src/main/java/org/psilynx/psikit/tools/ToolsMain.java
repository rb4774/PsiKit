package org.psilynx.psikit.tools;

/**
 * Entry point for PsiKit desktop tools.
 *
 * Usage:
 *   gradlew :tools:run --args="inspect C:/path/to/log.rlog"
 *   gradlew :tools:run --args="shooter-dump C:/path/to/log.rlog"
 */
public final class ToolsMain {

    private ToolsMain() {}

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            printUsageAndExit();
            return;
        }

        String command = args[0];
        String[] rest = new String[Math.max(0, args.length - 1)];
        if (rest.length > 0) {
            System.arraycopy(args, 1, rest, 0, rest.length);
        }

        switch (command) {
            case "inspect":
                RlogInspect.main(rest);
                return;
            case "shooter-dump":
                ShooterReplayDump.main(rest);
                return;
            default:
                System.err.println("Unknown command: " + command);
                printUsageAndExit();
        }
    }

    private static void printUsageAndExit() {
        System.err.println("PsiKit tools\n");
        System.err.println("Commands:");
        System.err.println("  inspect <log.rlog>        Print metadata and BuildInfo outputs");
        System.err.println("  shooter-dump <log.rlog>   Dump shooter-related signals as CSV\n");
        System.err.println("Examples:");
        System.err.println("  gradlew :tools:run --args=\"inspect C:/path/to/log.rlog\"");
        System.err.println("  gradlew :tools:run --args=\"shooter-dump C:/path/to/log.rlog\"");
        System.exit(2);
    }
}
