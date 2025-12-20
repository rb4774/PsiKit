package org.psilynx.psikit.ftc

import org.psilynx.psikit.core.Logger

/**
 * Logs a Pedro Pathing `Follower` pose using AdvantageScope Pose2d schema.
 *
 * This logger uses reflection so PsiKit does not need a compile-time dependency on Pedro Pathing.
 * Expected API:
 * - follower.getPose() -> pose
 * - pose.getX(), pose.getY() (inches)
 * - pose.getHeading() (radians)
 */
class PedroFollowerPoseLogger(posePath: String) {

    private val pose2d: StructPoseInputs
    private val tableKey: String

    init {
        val normalized = normalizeKey(posePath)
        val lastSlash = normalized.lastIndexOf('/')
        if (lastSlash > 0) {
            tableKey = normalized.substring(0, lastSlash)
            val fieldKey = normalized.substring(lastSlash + 1)
            pose2d = StructPoseInputs(fieldKey, "")
        } else {
            tableKey = normalized
            pose2d = StructPoseInputs("value", "")
        }
    }

    /** Logs follower pose (inches/radians) if a compatible follower object is provided. */
    fun log(follower: Any?) {
        if (follower == null) return

        val pose = invokeNoArg(follower, "getPose") ?: return

        val xInches = invokeNoArg(pose, "getX") as? Number ?: return
        val yInches = invokeNoArg(pose, "getY") as? Number ?: return
        val headingRad = invokeNoArg(pose, "getHeading") as? Number ?: return

        logPoseInches(xInches.toDouble(), yInches.toDouble(), headingRad.toDouble())
    }

    /** Logs a pose (inches/radians). Useful if you already have pose values. */
    fun logPoseInches(xInches: Double, yInches: Double, headingRad: Double) {
        val xMeters = inchesToMeters(xInches)
        val yMeters = inchesToMeters(yInches)

        pose2d.set(xMeters, yMeters, headingRad)
        Logger.processInputs(tableKey, pose2d)
    }

    private fun normalizeKey(key: String?): String {
        if (key == null) return ""
        var result = key.trim()
        while (result.endsWith("/")) {
            result = result.substring(0, result.length - 1)
        }
        return result
    }

    private fun invokeNoArg(target: Any, methodName: String): Any? {
        return try {
            val method = target.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
            method?.invoke(target)
        } catch (_: Throwable) {
            null
        }
    }

    private fun inchesToMeters(inches: Double): Double = inches * 0.0254
}
