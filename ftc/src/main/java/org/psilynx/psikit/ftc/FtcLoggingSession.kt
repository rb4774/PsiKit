package org.psilynx.psikit.ftc

import com.qualcomm.hardware.lynx.LynxModule
import com.qualcomm.hardware.lynx.LynxModule.BulkCachingMode.MANUAL
import com.qualcomm.robotcore.eventloop.opmode.Autonomous
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import org.psilynx.psikit.core.LoggableInputs
import org.psilynx.psikit.core.LogReplaySource
import org.psilynx.psikit.core.Logger
import org.psilynx.psikit.core.rlog.RLOGServer
import org.psilynx.psikit.core.rlog.RLOGWriter
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Composition-based PsiKit logging helper for FTC [LinearOpMode]s.
 *
 */
class FtcLoggingSession {

    /**
     * If true, logs Pinpoint odometry (when present) each loop via [PinpointOdometryLogger].
     *
     * Behavior when no Pinpoint is configured:
     * - Performs a one-time scan of the [com.qualcomm.robotcore.hardware.HardwareMap]
     * - Then becomes a no-op (no outputs are produced)
     *
     * Set to false to opt out even if a Pinpoint is present.
     */
    @JvmField
    var enablePinpointOdometryLogging: Boolean = true

    private val driverStationLogger = DriverStationLogger()
    private val pinpointOdometryLogger = PinpointOdometryLogger()

    private var wrappedHardwareMap: com.qualcomm.robotcore.hardware.HardwareMap? = null
    private var allHubs: List<LynxModule>? = null

    @JvmOverloads
    fun start(
        opMode: LinearOpMode,
        rlogPort: Int,
        filename: String = defaultLogFilename(opMode),
        folder: String = "/sdcard/FIRST/PsiKit/",
        replaySource: LogReplaySource? = null,
    ) {
        // If the prior OpMode was force-stopped, PsiKit may still be "running".
        try {
            Logger.end()
        } catch (_: Exception) {
            // ignore
        }
        Logger.reset()

        // Optional: configure replay before Logger.start().
        if (replaySource != null) {
            Logger.setReplay(true)
            Logger.setReplaySource(replaySource)
        }

        // Wrap hardwareMap for /HardwareMap/... inputs and replay manifest.
        val existingHardwareMap = opMode.hardwareMap
        if (existingHardwareMap != null) {
            opMode.hardwareMap = HardwareMapWrapper(existingHardwareMap)
            wrappedHardwareMap = opMode.hardwareMap

            // Configure Lynx bulk caching like PsiKitLinearOpMode.
            allHubs = try {
                val hubs = opMode.hardwareMap.getAll(LynxModule::class.java)
                for (hub in hubs) {
                    hub.bulkCachingMode = MANUAL
                }
                hubs
            } catch (_: Throwable) {
                null
            }
        } else {
            wrappedHardwareMap = null
            allHubs = null
        }

        // Record basic OpMode metadata like PsiKit's base classes do.
        recordOpModeMetadata(opMode)

        // Port 0 (or negative) disables the server. Useful in tests and competitions.
        if (rlogPort > 0) {
            Logger.addDataReceiver(RLOGServer(rlogPort))
        }
        // Blank filename disables file output. Useful for replay tests.
        if (filename.isNotBlank()) {
            Logger.addDataReceiver(RLOGWriter(folder, filename))
        }

        Logger.start()
    }

    fun end() {
        try {
            Logger.end()
        } catch (_: Exception) {
            // ignore
        }
    }

    /** Call once per loop, after [Logger.periodicBeforeUser]. */
    fun logOncePerLoop(opMode: LinearOpMode) {
        clearBulkCaches()

        if (!Logger.isReplay()) {
            OpModeControls.started = opMode.isStarted
            OpModeControls.stopped = opMode.isStopRequested
        }
        Logger.processInputs("OpModeControls", OpModeControls)

        // In replay, drive the OpMode's state from the log so init/start/stop loops can be
        // reproduced faithfully.
        if (Logger.isReplay()) {
            applyOpModeControls(opMode, OpModeControls.started, OpModeControls.stopped)
        }

        // DriverStation inputs (AdvantageScope Joysticks schema).
        driverStationLogger.log(opMode.gamepad1, opMode.gamepad2)

        if (enablePinpointOdometryLogging) {
            // Pinpoint odometry (AdvantageScope Pose2d/Pose3d structs under /Odometry).
            pinpointOdometryLogger.logAll(opMode.hardwareMap)
        }

        // Log all accessed hardware devices.
        for ((key, value) in HardwareMapWrapper.devicesToProcess) {
            val startNs = System.nanoTime()
            Logger.processInputs("HardwareMap/$key", value)
            val endNs = System.nanoTime()
            Logger.recordOutput("PsiKit/logTimes (us)/$key", (endNs - startNs) / 1_000.0)
        }
    }

    private fun clearBulkCaches() {
        val hubs = allHubs ?: return
        for (hub in hubs) {
            try {
                hub.clearBulkCache()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun defaultLogFilename(opMode: LinearOpMode): String {
        return opMode::class.java.simpleName +
            "_log_" +
            SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(Date()) +
            ".rlog"
    }

    private fun recordOpModeMetadata(opMode: LinearOpMode) {
        val teleOp = opMode::class.java.getAnnotation(TeleOp::class.java)
        if (teleOp != null) {
            Logger.recordMetadata("OpMode Name", teleOp.name)
            Logger.recordMetadata("OpMode type", "TeleOp")
            return
        }

        val auto = opMode::class.java.getAnnotation(Autonomous::class.java)
        if (auto != null) {
            Logger.recordMetadata("OpMode Name", auto.name)
            Logger.recordMetadata("OpMode type", "Autonomous")
            return
        }

        Logger.recordMetadata("OpMode Name", opMode::class.java.simpleName)
        Logger.recordMetadata("OpMode type", "Unknown")
    }

    private fun applyOpModeControls(opMode: LinearOpMode, started: Boolean, stopped: Boolean) {
        // FTC SDK (RobotCore 11.0.0): LinearOpMode's opModeInInit()/opModeIsActive() ultimately
        // depend on internal fields on OpModeInternal (superclass of OpMode).
        // Those fields are not declared on OpMode itself, so we must search the class hierarchy.
        setBooleanFieldIfPresent(opMode, "isStarted", started)
        setBooleanFieldIfPresent(opMode, "stopRequested", stopped)
    }

    private fun setBooleanFieldIfPresent(target: Any, fieldName: String, value: Boolean): Boolean {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                field.setBoolean(target, value)
                return true
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            } catch (_: Throwable) {
                return false
            }
        }
        return false
    }
}
