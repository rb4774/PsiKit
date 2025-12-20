package org.psilynx.psikit.ftc.wrappers

import com.qualcomm.robotcore.hardware.Gamepad
import org.psilynx.psikit.core.LogTable
import org.psilynx.psikit.core.LoggableInputs

class GamepadWrapper(val gamepad: Gamepad?): Gamepad(), LoggableInputs {
    override fun toLog(table: LogTable) {
        val gp = gamepad
        if (gp == null) {
            table.put("ButtonCount", NUM_BUTTONS)
            table.put("ButtonValues", 0L)
            table.put("AxisValues", doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
            table.put("POVs", intArrayOf(-1))
            table.put("TouchpadAxes", doubleArrayOf(0.0, 0.0, 0.0, 0.0))
            table.put("TouchpadFinger1", false)
            table.put("TouchpadFinger2", false)
            table.put("Triangle", false)
            table.put("Circle", false)
            table.put("Cross", false)
            table.put("Square", false)
            table.put("Options", false)
            table.put("Share", false)
            table.put("DpadUp", false)
            table.put("DpadRight", false)
            table.put("DpadDown", false)
            table.put("DpadLeft", false)
            return
        }

        // AdvantageScope Joysticks schema (DriverStation/JoystickN):
        // - ButtonCount (int)
        // - ButtonValues (int, bit0 = button1)
        // - AxisValues (float[])
        // - POVs (int[], degrees or -1)
        //
        // Keep the required fields stable, but also log extra FTC Gamepad state as additional keys.
        table.put("ButtonCount", NUM_BUTTONS)
        table.put(
            "ButtonValues",
            listOf(
                // msb
                gp.touchpad,
                gp.guide,
                gp.right_stick_button,
                gp.left_stick_button,
                gp.start,
                gp.back,
                gp.right_bumper,
                gp.left_bumper,
                gp.y,
                gp.x,
                gp.b,
                gp.a,
                // lsb (a)
            ).fold(0L) { acc, value -> (acc shl 1) + if (value) 1L else 0L }
        )
        table.put(
            "AxisValues",
            // AdvantageScope Joysticks expects 6 axis values.
            // Put extra axes under a separate key to avoid breaking visualization.
            doubleArrayOf(
                gp.left_stick_x.toDouble(),
                gp.left_stick_y.toDouble(),
                gp.left_trigger.toDouble(),
                gp.right_trigger.toDouble(),
                gp.right_stick_x.toDouble(),
                gp.right_stick_y.toDouble(),
            )
        )
        table.put(
            "TouchpadAxes",
            doubleArrayOf(
                gp.touchpad_finger_1_x.toDouble(),
                gp.touchpad_finger_1_y.toDouble(),
                gp.touchpad_finger_2_x.toDouble(),
                gp.touchpad_finger_2_y.toDouble(),
            )
        )
        table.put(
            "POVs",
            intArrayOf(
                if (gp.dpad_up) 0
                else if (gp.dpad_right) 90
                else if (gp.dpad_down) 180
                else if (gp.dpad_left) 270
                else -1
            )
        ) // only one POV (dpad)

        // Extra fields (kept separate so the Joysticks tab stays happy even if it ignores them)
        table.put("TouchpadFinger1", gp.touchpad_finger_1)
        table.put("TouchpadFinger2", gp.touchpad_finger_2)
        table.put("Triangle", gp.triangle)
        table.put("Circle", gp.circle)
        table.put("Cross", gp.cross)
        table.put("Square", gp.square)
        table.put("Options", gp.options)
        table.put("Share", gp.share)
        table.put("DpadUp", gp.dpad_up)
        table.put("DpadRight", gp.dpad_right)
        table.put("DpadDown", gp.dpad_down)
        table.put("DpadLeft", gp.dpad_left)

        // Mirror the underlying state into this Gamepad instance for user code + replay.
        touchpad_finger_1_x = gp.touchpad_finger_1_x
        touchpad_finger_1_y = gp.touchpad_finger_1_y
        touchpad_finger_2_x = gp.touchpad_finger_2_x
        touchpad_finger_2_y = gp.touchpad_finger_2_y
        right_stick_button = gp.right_stick_button
        left_stick_button = gp.left_stick_button
        touchpad_finger_1 = gp.touchpad_finger_1
        touchpad_finger_2 = gp.touchpad_finger_2
        right_stick_x = gp.right_stick_x
        right_stick_y = gp.right_stick_y
        right_trigger = gp.right_trigger
        left_stick_x = gp.left_stick_x
        left_stick_y = gp.left_stick_y
        left_trigger = gp.left_trigger
        right_bumper = gp.right_bumper
        left_bumper = gp.left_bumper
        dpad_right = gp.dpad_right
        dpad_left = gp.dpad_left
        dpad_down = gp.dpad_down
        dpad_up = gp.dpad_up
        triangle = gp.triangle
        touchpad = gp.touchpad
        options = gp.options
        circle = gp.circle
        square = gp.square
        guide = gp.guide
        start = gp.start
        cross = gp.cross
        share = gp.share
        back = gp.back
        a = gp.a
        b = gp.b
        x = gp.x
        y = gp.y
    }

    override fun fromLog(table: LogTable) {
        var valuesInt = table.get("ButtonValues", 0L)
        val valuesList = mutableListOf<Boolean>()
        repeat(NUM_BUTTONS) {
            valuesList.add(valuesInt % 2L == 1L)
            valuesInt = valuesInt shr 1
        }

        valuesList.reverse() // decoding puts lsb in first, needs to be last

        touchpad = valuesList[0]
        guide = valuesList[1]
        right_stick_button = valuesList[2]
        left_stick_button = valuesList[3]
        start = valuesList[4]
        back = valuesList[5]
        right_bumper = valuesList[6]
        left_bumper = valuesList[7]
        y = valuesList[8]
        x = valuesList[9]
        b = valuesList[10]
        a = valuesList[11]

        // AxisValues: prefer the new 6-length double[] schema.
        val axisValuesD = table.get("AxisValues", doubleArrayOf())
        if (axisValuesD.isNotEmpty()) {
            left_stick_x = axisValuesD.getOrElse(0) { 0.0 }.toFloat()
            left_stick_y = axisValuesD.getOrElse(1) { 0.0 }.toFloat()
            left_trigger = axisValuesD.getOrElse(2) { 0.0 }.toFloat()
            right_trigger = axisValuesD.getOrElse(3) { 0.0 }.toFloat()
            right_stick_x = axisValuesD.getOrElse(4) { 0.0 }.toFloat()
            right_stick_y = axisValuesD.getOrElse(5) { 0.0 }.toFloat()
        } else {
            // Back-compat: older logs used float[] (and sometimes packed extra axes after index 6).
            val axisValuesF = table.get("AxisValues", floatArrayOf())
            if (axisValuesF.isNotEmpty()) {
                left_stick_x = axisValuesF.getOrElse(0) { 0f }
                left_stick_y = axisValuesF.getOrElse(1) { 0f }
                left_trigger = axisValuesF.getOrElse(2) { 0f }
                right_trigger = axisValuesF.getOrElse(3) { 0f }
                right_stick_x = axisValuesF.getOrElse(4) { 0f }
                right_stick_y = axisValuesF.getOrElse(5) { 0f }
                touchpad_finger_1_x = axisValuesF.getOrElse(6) { 0f }
                touchpad_finger_1_y = axisValuesF.getOrElse(7) { 0f }
                touchpad_finger_2_x = axisValuesF.getOrElse(8) { 0f }
                touchpad_finger_2_y = axisValuesF.getOrElse(9) { 0f }
            }
        }

        // Touchpad axes (new key). If missing, it may have been packed into the old AxisValues.
        val touchAxes = table.get("TouchpadAxes", doubleArrayOf())
        if (touchAxes.isNotEmpty()) {
            touchpad_finger_1_x = touchAxes.getOrElse(0) { 0.0 }.toFloat()
            touchpad_finger_1_y = touchAxes.getOrElse(1) { 0.0 }.toFloat()
            touchpad_finger_2_x = touchAxes.getOrElse(2) { 0.0 }.toFloat()
            touchpad_finger_2_y = touchAxes.getOrElse(3) { 0.0 }.toFloat()
        }

        // Extra fields (may not exist in older logs)
        touchpad_finger_1 = table.get("TouchpadFinger1", false)
        touchpad_finger_2 = table.get("TouchpadFinger2", false)
        triangle = table.get("Triangle", false)
        circle = table.get("Circle", false)
        cross = table.get("Cross", false)
        square = table.get("Square", false)
        options = table.get("Options", false)
        share = table.get("Share", false)

        // POV (dpad)
        dpad_up = false
        dpad_right = false
        dpad_down = false
        dpad_left = false
        val povs = table.get("POVs", intArrayOf(-1))
        val povDir = if (povs.isNotEmpty()) povs[0] else -1
        if (povDir == 0) dpad_up = true
        if (povDir == 90) dpad_right = true
        if (povDir == 180) dpad_down = true
        if (povDir == 270) dpad_left = true

    }
    companion object {
        const val NUM_BUTTONS = 12
    }
}
