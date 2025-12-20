package org.psilynx.psikit.tools;

import org.psilynx.psikit.core.LogTable;
import org.psilynx.psikit.core.rlog.RLOGReplay;

import java.io.PrintWriter;

/**
 * Desktop CLI that replays an RLOG file and dumps shooter-relevant signals as CSV.
 *
 * Usage:
 *   gradlew :tools:run --args="C:/path/to/log.rlog"
 *
 * If no args are provided, this uses {@link RLOGReplay#promptForPath()}.
 */
public final class ShooterReplayDump {

    private ShooterReplayDump() {}

    public static void main(String[] args) {
        String logPath = (args != null && args.length > 0 && args[0] != null && !args[0].isBlank())
            ? args[0]
            : RLOGReplay.promptForPath();

        RLOGReplay replay = new RLOGReplay(logPath);
        replay.start();

        try (PrintWriter out = new PrintWriter(System.out, true)) {
            out.println(String.join(",",
                "t",
                "visionTx",
                "flyLeftVel",
                "flyRightVel",
                "flyLeftPower",
                "flyRightPower",
                "turretPower",
                "turretVoltage",
                "turretDeg",
                "hoodPos",
                "lightPos"
            ));

            while (true) {
                LogTable entry = replay.getEntry();
                if (entry == null) {
                    break;
                }

                double t = entry.getTimestamp();

                // Values logged by TeamCode via Logger.recordOutput("visionError", ...)
                double visionTx = getDouble(entry.getSubtable("RealOutputs"), "visionError", Double.NaN);

                LogTable hw = entry.getSubtable("HardwareMap");

                LogTable flyLeft = hw.getSubtable("fly_left");
                LogTable flyRight = hw.getSubtable("fly_right");
                double flyLeftVel = getDouble(flyLeft, "currentVel", Double.NaN);
                double flyRightVel = getDouble(flyRight, "currentVel", Double.NaN);
                double flyLeftPower = getDouble(flyLeft, "power", Double.NaN);
                double flyRightPower = getDouble(flyRight, "power", Double.NaN);

                LogTable turret = hw.getSubtable("turret");
                double turretPower = getDouble(turret, "power", Double.NaN);

                LogTable turretAnalog = hw.getSubtable("turret_analog");
                double turretVoltage = getDouble(turretAnalog, "voltage", Double.NaN);
                double turretDeg = voltageToDeg(turretVoltage, 3.3);

                LogTable hood = hw.getSubtable("hood");
                double hoodPos = getDouble(hood, "Position", Double.NaN);

                LogTable light = hw.getSubtable("light");
                double lightPos = getDouble(light, "Position", Double.NaN);

                out.println(csv(
                    t,
                    visionTx,
                    flyLeftVel,
                    flyRightVel,
                    flyLeftPower,
                    flyRightPower,
                    turretPower,
                    turretVoltage,
                    turretDeg,
                    hoodPos,
                    lightPos
                ));
            }
        } finally {
            replay.end();
        }
    }

    private static String csv(double... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            double v = values[i];
            if (Double.isNaN(v)) {
                sb.append("NaN");
            } else {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    private static double getDouble(LogTable table, String key, double defaultValue) {
        try {
            return table.get(key, defaultValue);
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    // Matches AbsoluteAnalogEncoder defaults used in your Shooter ctor:
    // posDeg = (1 - voltage / analogRange) * 360, normalized to [0, 360)
    private static double voltageToDeg(double voltage, double analogRange) {
        if (Double.isNaN(voltage) || analogRange <= 0.0) {
            return Double.NaN;
        }
        double deg = (1.0 - (voltage / analogRange)) * 360.0;
        deg = (deg % 360.0 + 360.0) % 360.0;
        return deg;
    }
}
