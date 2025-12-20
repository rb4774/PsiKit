package org.psilynx.psikit.ftc

import com.qualcomm.hardware.lynx.LynxModule
import com.qualcomm.hardware.lynx.LynxModule.BulkCachingMode.MANUAL
import com.qualcomm.robotcore.eventloop.opmode.Autonomous
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import org.psilynx.psikit.core.LoggableInputs
import org.psilynx.psikit.core.Logger
import org.psilynx.psikit.core.rlog.RLOGServer
import org.psilynx.psikit.core.rlog.RLOGWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject
import android.content.Context
import android.preference.PreferenceManager

/**
 * Composition-based PsiKit logging helper for FTC [LinearOpMode]s.
 *
 */
class FtcLoggingSession {

    private val userMetadata: LinkedHashMap<String, String> = LinkedHashMap()

    /**
     * Records metadata to be published under /RealMetadata (or /ReplayMetadata) at session start.
     *
     * Prefer this over calling Logger.recordMetadata() directly when using FtcLoggingSession,
     * because start() performs a Logger.reset() which would otherwise wipe recorded metadata.
     */
    fun recordMetadata(key: String, value: String) {
        userMetadata[key] = value
    }

    /** Clears metadata previously added via recordMetadata(). */
    fun clearMetadata() {
        userMetadata.clear()
    }

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
    fun start(opMode: LinearOpMode, rlogPort: Int, filename: String = defaultLogFilename(opMode)) {
        // If the prior OpMode was force-stopped, PsiKit may still be "running".
        try {
            Logger.end()
        } catch (_: Exception) {
            // ignore
        }
        Logger.reset()

        // Apply user-supplied metadata after reset (so it isn't wiped).
        for ((key, value) in userMetadata) {
            Logger.recordMetadata(key, value)
        }

        // Wrap hardwareMap for /HardwareMap/... inputs and replay manifest.
        opMode.hardwareMap = HardwareMapWrapper(opMode.hardwareMap)
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

        // Record basic OpMode metadata like PsiKit's base classes do.
        recordOpModeMetadata(opMode)

        // Record build metadata (Git SHA/branch/date, build date) when available.
        recordBuildInfoMetadata(opMode)

        Logger.addDataReceiver(RLOGServer(rlogPort))
        Logger.addDataReceiver(RLOGWriter(filename))

        if (Logger.isReplay()) {
            // Best-effort: avoid blocking forever in waitForStart()/opModeInInit().
            forceOpModeStarted(opMode)
        }

        Logger.start()
    }

    private fun recordBuildInfoMetadata(opMode: LinearOpMode) {
        val candidateClasses = listOf(
            // Preferred stable package when using the PsiKit buildinfo Gradle plugin.
            "org.psilynx.psikit.buildinfo.PsiKitBuildInfo",
            // Backward-compatibility for older consumers that generated into TeamCode.
            "org.firstinspires.ftc.teamcode.PsiKitBuildInfo",
        )

        val candidateLoaders: List<ClassLoader?> = listOf(
            // FTC/RC app often runs user code under a distinct classloader.
            opMode::class.java.classLoader,
            Thread.currentThread().contextClassLoader,
            FtcLoggingSession::class.java.classLoader,
        )

        var clazz: Class<*>? = null
        outer@ for (name in candidateClasses) {
            for (loader in candidateLoaders) {
                try {
                    clazz = if (loader != null) {
                        Class.forName(name, false, loader)
                    } else {
                        Class.forName(name)
                    }
                    break@outer
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }
        if (clazz == null) return

        fun getString(field: String): String? {
            return try {
                clazz.getField(field).get(null) as? String
            } catch (_: Throwable) {
                null
            }
        }

        fun getInt(field: String): Int? {
            return try {
                (clazz.getField(field).get(null) as? Int)
            } catch (_: Throwable) {
                null
            }
        }

        getString("GIT_SHA")?.let { Logger.recordMetadata("GitSHA", it) }
        getString("GIT_BRANCH")?.let { Logger.recordMetadata("GitBranch", it) }
        getString("GIT_DATE")?.let { Logger.recordMetadata("GitDate", it) }
        getString("BUILD_DATE")?.let { Logger.recordMetadata("BuildDate", it) }

        val dirty = getInt("DIRTY")
        if (dirty != null) {
            Logger.recordMetadata(
                "GitDirty",
                when (dirty) {
                    0 -> "false"
                    1 -> "true"
                    else -> "unknown"
                }
            )
        }
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

        OpModeControls.started = opMode.isStarted
        OpModeControls.stopped = opMode.isStopRequested
        Logger.processInputs("OpModeControls", OpModeControls)

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
        // General runtime/environment metadata.
        Logger.recordMetadata("RuntimeType", "FTC")
        Logger.recordMetadata("DeviceManufacturer", android.os.Build.MANUFACTURER ?: "")
        Logger.recordMetadata("DeviceModel", android.os.Build.MODEL ?: "")
        Logger.recordMetadata("DeviceProduct", android.os.Build.PRODUCT ?: "")
        Logger.recordMetadata("AndroidRelease", android.os.Build.VERSION.RELEASE ?: "")
        Logger.recordMetadata("AndroidSdkInt", android.os.Build.VERSION.SDK_INT.toString())

        // PsiKit version metadata (best-effort; BuildConfig may not be generated for library modules).
        try {
            val buildConfig = Class.forName("org.psilynx.psikit.ftc.BuildConfig")
            val versionName = buildConfig.getField("VERSION_NAME").get(null) as? String
            if (!versionName.isNullOrBlank()) {
                Logger.recordMetadata("PsiKitVersion", versionName)
            }
        } catch (_: Throwable) {
            // ignore
        }

        val utc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        Logger.recordMetadata("SessionStart", utc.format(Date()))

        // Robot configuration (best-effort; varies by SDK/app versions).
        try {
            val ctx = opMode.hardwareMap.appContext
            val configName = readRobotConfigName(ctx)
            if (!configName.isNullOrBlank()) {
                Logger.recordMetadata("RobotConfigName", configName)
            }
        } catch (_: Throwable) {
            // ignore
        }

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

    private fun readRobotConfigName(ctx: Context): String? {
        // Try using the RC app's config manager via reflection.
        try {
            val mgrClass = Class.forName("com.qualcomm.ftccommon.configuration.RobotConfigFileManager")
            val ctor = mgrClass.getConstructor(Context::class.java)
            val mgr = ctor.newInstance(ctx)

            // RobotConfigFileManager#getActiveConfig() -> RobotConfigFile
            val activeConfig = mgrClass.getMethod("getActiveConfig").invoke(mgr)
            if (activeConfig != null) {
                // Prefer a simple config name if available.
                val name = try { activeConfig.javaClass.getMethod("getName").invoke(activeConfig) as? String } catch (_: Throwable) { null }
                val normalized = normalizeRobotConfigName(name)
                if (!normalized.isNullOrBlank()) return normalized

                // Some SDK builds may serialize config info as JSON in toString().
                val toStr = try { activeConfig.toString() } catch (_: Throwable) { null }
                val fromToString = normalizeRobotConfigName(toStr)
                if (!fromToString.isNullOrBlank()) return fromToString
            }
        } catch (_: Throwable) {
            // ignore
        }

        // Fallback: try preference keys that often store the last selected config name/path.
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val candidates = listOf(
            "pref_hardware_config_filename",
            "pref_hardware_config",
            "pref_active_hardware_config",
            "active_hardware_config",
            "hardware_config",
            "hardware_config_name",
            "robot_config_name"
        )
        for (k in candidates) {
            val v = prefs.getString(k, null)
            val normalized = normalizeRobotConfigName(v)
            if (!normalized.isNullOrBlank()) return normalized
        }
        return null
    }

    private fun normalizeRobotConfigName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        // If the preference stores a JSON object, extract the "name" field.
        if (trimmed.startsWith("{") && trimmed.contains("\"name\"")) {
            try {
                val obj = JSONObject(trimmed)
                val name = obj.optString("name", "")
                if (name.isNotBlank()) return name
            } catch (_: Throwable) {
                // ignore
            }
        }

        return trimmed
    }

    private fun forceOpModeStarted(opMode: LinearOpMode) {
        try {
            val startedField = OpMode::class.java.getDeclaredField("isStarted")
            startedField.isAccessible = true
            startedField.setBoolean(opMode, true)
        } catch (_: Throwable) {
            // ignore
        }
    }
}
