package org.psilynx.psikit.ftc.wrappers

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit
import org.psilynx.psikit.core.LogTable
import org.psilynx.psikit.core.Logger
import org.psilynx.psikit.ftc.StructPoseInputs

/**
 * Lightweight log adapter for the FTC SDK's goBILDA Pinpoint driver
 * (com.qualcomm.hardware.gobilda.GoBildaPinpointDriver).
 *
 * Design goal: follow the same model as other hardware wrappers:
 * - Pinpoint is only logged if user code accesses it via hardwareMap.get(...)
 * - PsiKit does not "own" update(); it only reads/logs state
 */
class SdkPinpointWrapper(
    private val device: GoBildaPinpointDriver?,
) : HardwareInput<GoBildaPinpointDriver> {

    /** Set by [org.psilynx.psikit.ftc.HardwareMapWrapper] so we can also emit /Odometry/<name>. */
    var psikitName: String? = null

    private val poses = StructPoseInputs("Pose2d", "Pose3d")

    private var cacheFilled = false
    private var cachedDeviceId: Int = 0
    private var cachedDeviceVersion: Int = 0
    private var cachedYawScalar: Float = 0f
    private var cachedXOffsetMm: Float = 0f
    private var cachedYOffsetMm: Float = 0f

    override fun new(wrapped: GoBildaPinpointDriver?): HardwareInput<GoBildaPinpointDriver> = SdkPinpointWrapper(wrapped)

    override fun toLog(table: LogTable) {
        val target = device ?: return

        // NOTE: PsiKit does not call update() here; consumer code (Pedro) owns updates.
        val pose: Pose2D? = target.position

        // These getters do their own I2C reads in the SDK driver; cache them once.
        if (!cacheFilled) {
            cachedDeviceId = target.deviceID
            cachedDeviceVersion = target.deviceVersion
            cachedYawScalar = target.yawScalar
            cachedXOffsetMm = target.getXOffset(DistanceUnit.MM)
            cachedYOffsetMm = target.getYOffset(DistanceUnit.MM)
            cacheFilled = true
        }

        // Common raw fields (match PinpointWrapper naming).
        table.put("deviceId", cachedDeviceId)
        table.put("deviceVersion", cachedDeviceVersion)
        table.put("yawScalar", cachedYawScalar)
        table.put("xOffset", cachedXOffsetMm)
        table.put("yOffset", cachedYOffsetMm)

        table.put("xEncoderValue", target.encoderX)
        table.put("yEncoderValue", target.encoderY)
        table.put("loopTime", target.loopTime)
        table.put("deviceStatus", target.deviceStatus.toString())

        table.put("xPosition", target.getPosX(DistanceUnit.MM))
        table.put("yPosition", target.getPosY(DistanceUnit.MM))
        table.put("hOrientation", target.getHeading(UnnormalizedAngleUnit.RADIANS))

        table.put("xVelocity", target.getVelX(DistanceUnit.MM))
        table.put("yVelocity", target.getVelY(DistanceUnit.MM))
        table.put("hVelocity", target.getHeadingVelocity(UnnormalizedAngleUnit.RADIANS))

        // SDK 11's GoBildaPinpointDriver does not expose quaternion/pitch/roll.

        if (pose == null) {
            // Still emit /Odometry only when we have a pose.
            return
        }

        val xMeters = pose.getX(DistanceUnit.METER)
        val yMeters = pose.getY(DistanceUnit.METER)
        val headingRad = pose.getHeading(AngleUnit.RADIANS)

        // Convenient pose view (these keys are new; raw keys above match legacy naming).
        table.put("xMeters", xMeters)
        table.put("yMeters", yMeters)
        table.put("headingRad", headingRad)

        // Provide a convenient Odometry schema for AdvantageScope field widgets.
        val name = psikitName
        if (!name.isNullOrBlank()) {
            poses.set(xMeters, yMeters, headingRad)
            Logger.processInputs("/Odometry/$name", poses)
        }
    }

    override fun fromLog(table: LogTable) {
        // No-op: this wrapper is for recording/telemetry, not for replay-driving hardware.
    }
}
