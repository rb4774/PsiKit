package org.psilynx.psikit.ftc

import com.qualcomm.robotcore.hardware.HardwareDevice
import com.qualcomm.robotcore.hardware.HardwareMap
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D
import org.psilynx.psikit.core.Logger

/**
 * Logs goBILDA Pinpoint pose in AdvantageScope-friendly schemas for 2D/3D visualizations.
 *
 * Produces:
 * - `/Odometry/<deviceName>` with `Pose2d` and `Pose3d` struct fields
 * - `/Odometry` alias when there's exactly one Pinpoint configured
 */
class PinpointOdometryLogger {

    private class NamedPinpoint(
        val name: String,
        val update: () -> Unit,
        val position: () -> Pose2D,
        val poses: StructPoseInputs,
    ) {
        var lastSampleNs: Long = Long.MIN_VALUE
        var lastXMeters: Double = 0.0
        var lastYMeters: Double = 0.0
        var lastHeadingRad: Double = 0.0
    }

    private val cached = mutableListOf<NamedPinpoint>()
    private var cachedOnce = false

    private val robotAliases = StructPoseInputs("RobotPose", "RobotPose3d")

    private fun secondsSince(ns: Long): Double {
        if (ns == Long.MIN_VALUE) return Double.POSITIVE_INFINITY
        return (System.nanoTime() - ns) / 1_000_000_000.0
    }

    private fun shouldSampleNow(device: NamedPinpoint): Boolean {
        val period = FtcLogTuning.pinpointReadPeriodSec
        if (period <= 0.0) return true
        return secondsSince(device.lastSampleNs) >= period
    }

    fun logAll(hardwareMap: HardwareMap) {
        if (!cachedOnce) {
            cacheDevices(hardwareMap)
            cachedOnce = true
        }

        for (device in cached) {
            if (shouldSampleNow(device)) {
                device.lastSampleNs = System.nanoTime()
                device.update()
                val pose = device.position()
                device.lastXMeters = pose.getX(DistanceUnit.METER)
                device.lastYMeters = pose.getY(DistanceUnit.METER)
                device.lastHeadingRad = pose.getHeading(AngleUnit.RADIANS)
            }

            device.poses.set(device.lastXMeters, device.lastYMeters, device.lastHeadingRad)
            Logger.processInputs("/Odometry/${device.name}", device.poses)

            if (cached.size == 1) {
                robotAliases.set(device.lastXMeters, device.lastYMeters, device.lastHeadingRad)
                Logger.processInputs("/Odometry", robotAliases)
            }
        }
    }

    private fun cacheDevices(hardwareMap: HardwareMap) {
        cached.clear()

        if (FtcLogTuning.pinpointUseMinimalBulkReadScope) {
            tryConfigureMinimalScopeForPsiKitPinpoint(hardwareMap)
        }

        // 1) FTC SDK's goBILDA driver (2025+ SDKs): com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
        // Use reflection so PsiKit can still compile against older SDK variants.
        val sdkDevices = getAllByClassName(
            hardwareMap,
            "com.qualcomm.hardware.gobilda.GoBildaPinpointDriver",
        )
        for (device in sdkDevices) {
            val hw = device as? HardwareDevice
            val name = if (hw != null) firstNameOrFallback(hardwareMap, hw, "pinpoint") else "pinpoint"
            val updateFn = if (FtcLogTuning.pinpointLoggerCallsUpdate) {
                { invokeNoArg(device, "update") }
            } else {
                { }
            }
            val positionFn = { invokePosition(device) }
            cached.add(NamedPinpoint(name, updateFn, positionFn, StructPoseInputs("Pose2d", "Pose3d")))
        }

        // 2) PsiKit's embedded driver (still supported)
        // Only use it when the SDK driver is not present to avoid double Pinpoint instances
        // and extra overhead on newer SDKs.
        if (sdkDevices.isEmpty()) {
            // Important: if the OpMode hardwareMap is wrapped (HardwareMapWrapper), calling
            // getAll(...) on the wrapper will create PinpointWrapper entries and register them
            // into HardwareMapWrapper.devicesToProcess. That can cause Pinpoint to be processed
            // twice each loop (once via /Odometry and once via /HardwareMap/<name>).
            // Prefer the underlying SDK HardwareMap when available.
            val baseMap = (hardwareMap as? HardwareMapWrapper)?.hardwareMap
            val mapForPsiKitDriver = baseMap ?: hardwareMap

            val psikitDevices = try {
                mapForPsiKitDriver.getAll(GoBildaPinpointDriver::class.java)
            } catch (_: Throwable) {
                emptyList()
            }
            for (device in psikitDevices) {
                val name = firstNameOrFallback(hardwareMap, device, "pinpoint")
                cached.add(
                    NamedPinpoint(
                        name,
                        update = if (FtcLogTuning.pinpointLoggerCallsUpdate) {
                            { device.update() }
                        } else {
                            { }
                        },
                        position = { device.position },
                        poses = StructPoseInputs("Pose2d", "Pose3d"),
                    )
                )
            }
        }

        cached.sortBy { it.name }
    }

    private fun tryConfigureMinimalScopeForPsiKitPinpoint(hardwareMap: HardwareMap) {
        // If the OpMode hardwareMap is wrapped, calling getAll(...) on the wrapper can create
        // PinpointWrapper entries and register them into HardwareMapWrapper.devicesToProcess.
        // This method should be a pure configuration step, not a side-effect that enables
        // additional HardwareMap logging.
        val baseMap = (hardwareMap as? HardwareMapWrapper)?.hardwareMap
        val mapForPsiKitDriver = baseMap ?: hardwareMap

        // Only PsiKit's embedded driver exposes Register + setBulkReadScope in this repo.
        val devices = try {
            mapForPsiKitDriver.getAll(GoBildaPinpointDriver::class.java)
        } catch (_: Throwable) {
            emptyList()
        }

        for (device in devices) {
            try {
                // Keep loopTime + status for LocalTest error detection.
                device.setBulkReadScope(
                    GoBildaPinpointDriver.Register.DEVICE_STATUS,
                    GoBildaPinpointDriver.Register.LOOP_TIME,
                    GoBildaPinpointDriver.Register.X_POSITION,
                    GoBildaPinpointDriver.Register.Y_POSITION,
                    GoBildaPinpointDriver.Register.H_ORIENTATION,
                )
            } catch (_: Throwable) {
                // Ignore: firmware V1/V2 doesn't support scope changes.
            }
        }
    }

    private fun firstNameOrFallback(
        hardwareMap: HardwareMap,
        device: HardwareDevice,
        fallback: String,
    ): String {
        return try {
            val names = hardwareMap.getNamesOf(device)
            if (!names.isNullOrEmpty()) names.first() else fallback
        } catch (_: Throwable) {
            fallback
        }
    }

    private fun getAllByClassName(hardwareMap: HardwareMap, className: String): List<Any> {
        val clazz = try {
            Class.forName(className)
        } catch (_: Throwable) {
            return emptyList()
        }

        @Suppress("UNCHECKED_CAST")
        return try {
            hardwareMap.getAll(clazz as Class<Any>) as? List<Any> ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun invokeNoArg(target: Any, methodName: String) {
        try {
            val m = target.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
            m?.invoke(target)
        } catch (_: Throwable) {
            // Ignore; logging should never crash the OpMode.
        }
    }

    private fun invokePosition(target: Any): Pose2D {
        // Prefer getPosition() (SDK driver) but accept Kotlin property getter name too.
        val candidates = listOf("getPosition", "get_position")
        for (name in candidates) {
            try {
                val m = target.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
                val value = m?.invoke(target)
                if (value is Pose2D) {
                    return value
                }
            } catch (_: Throwable) {
                // Try next.
            }
        }

        // Fallback: try Kotlin-style property getter "getPosition" via declaredMethods.
        try {
            val m = target.javaClass.declaredMethods.firstOrNull { it.name == "getPosition" && it.parameterTypes.isEmpty() }
            m?.isAccessible = true
            val value = m?.invoke(target)
            if (value is Pose2D) {
                return value
            }
        } catch (_: Throwable) {
        }

        return Pose2D(DistanceUnit.METER, 0.0, 0.0, AngleUnit.RADIANS, 0.0)
    }
}
