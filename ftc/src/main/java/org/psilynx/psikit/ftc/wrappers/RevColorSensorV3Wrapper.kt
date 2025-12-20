package org.psilynx.psikit.ftc.wrappers

import com.qualcomm.hardware.rev.RevColorSensorV3
import com.qualcomm.robotcore.hardware.HardwareDevice
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.psilynx.psikit.core.LogTable

class RevColorSensorV3Wrapper(
    private val device: RevColorSensorV3?,
) : HardwareInput<RevColorSensorV3> {

    private var _deviceName = "MockRevColorSensorV3"
    private var _connectionInfo = ""
    private var _manufacturer = HardwareDevice.Manufacturer.Other
    private var _version = 1

    private var _red = 0
    private var _green = 0
    private var _blue = 0
    private var _alpha = 0
    private var _argb = 0

    private var _distanceMm = Double.NaN
    private var _gain = Double.NaN

    override fun new(wrapped: RevColorSensorV3?) = RevColorSensorV3Wrapper(wrapped)

    override fun toLog(table: LogTable) {
        val d = device
        if (d != null) {
            _deviceName = d.deviceName
            _connectionInfo = d.connectionInfo
            _manufacturer = d.manufacturer
            _version = d.version

            _red = d.red()
            _green = d.green()
            _blue = d.blue()
            _alpha = d.alpha()
            _argb = d.argb()

            _distanceMm = tryInvokeDistanceMm(d)

            _gain = tryInvokeGain(d)
        }

        table.put("deviceName", _deviceName)
        table.put("connectionInfo", _connectionInfo)
        table.put("manufacturer", _manufacturer)
        table.put("version", _version)

        table.put("red", _red)
        table.put("green", _green)
        table.put("blue", _blue)
        table.put("alpha", _alpha)
        table.put("argb", _argb)

        table.put("distanceMm", _distanceMm)
        table.put("gain", _gain)
    }

    override fun fromLog(table: LogTable) {
        _deviceName = table.get("deviceName", "MockRevColorSensorV3")
        _connectionInfo = table.get("connectionInfo", "")
        _manufacturer = table.get("manufacturer", HardwareDevice.Manufacturer.Other)
        _version = table.get("version", 1)

        _red = table.get("red", 0)
        _green = table.get("green", 0)
        _blue = table.get("blue", 0)
        _alpha = table.get("alpha", 0)
        _argb = table.get("argb", 0)

        _distanceMm = table.get("distanceMm", Double.NaN)
        _gain = table.get("gain", Double.NaN)
    }

    private fun tryInvokeGain(d: Any): Double {
        return try {
            val m = d.javaClass.methods.firstOrNull {
                (it.name == "getGain" || it.name == "gain") && it.parameterTypes.isEmpty()
            } ?: return Double.NaN
            val v = m.invoke(d)
            when (v) {
                is Number -> v.toDouble()
                else -> Double.NaN
            }
        } catch (_: Throwable) {
            Double.NaN
        }
    }

    private fun tryInvokeDistanceMm(d: Any): Double {
        return try {
            val m = d.javaClass.methods.firstOrNull {
                it.name == "getDistance" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].name == DistanceUnit::class.java.name
            } ?: return Double.NaN
            val v = m.invoke(d, DistanceUnit.MM)
            (v as? Number)?.toDouble() ?: Double.NaN
        } catch (_: Throwable) {
            Double.NaN
        }
    }
}
