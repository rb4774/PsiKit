package org.psilynx.psikit.ftc.wrappers

import com.qualcomm.robotcore.hardware.DistanceSensor
import com.qualcomm.robotcore.hardware.HardwareDevice
import com.qualcomm.robotcore.hardware.NormalizedColorSensor
import com.qualcomm.robotcore.hardware.NormalizedRGBA
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.psilynx.psikit.core.LogTable

/**
 * Replay-safe wrapper for NormalizedColorSensor.
 *
 * Notes:
 * - Implements NormalizedColorSensor so HardwareMapWrapper can return it in replay.
 * - Does not assume every NormalizedColorSensor is also a DistanceSensor; distance is logged when available.
 */
class NormalizedColorSensorWrapper(
    private val device: NormalizedColorSensor?,
) : NormalizedColorSensor, DistanceSensor, HardwareInput<NormalizedColorSensor> {

    private var _deviceName = "MockNormalizedColorSensor"
    private var _connectionInfo = ""
    private var _manufacturer = HardwareDevice.Manufacturer.Other
    private var _version = 1

    private var _normalized = NormalizedRGBA()
    private var _gain = 1.0f

    private var _distanceMm = Double.NaN

    override fun new(wrapped: NormalizedColorSensor?) = NormalizedColorSensorWrapper(wrapped)

    override fun toLog(table: LogTable) {
        val d = device
        if (d != null) {
            _deviceName = d.deviceName
            _connectionInfo = d.connectionInfo
            _manufacturer = d.manufacturer
            _version = d.version

            _normalized = try {
                d.normalizedColors
            } catch (_: Throwable) {
                _normalized
            }

            _gain = try {
                d.gain
            } catch (_: Throwable) {
                _gain
            }

            _distanceMm = (d as? DistanceSensor)?.let {
                try {
                    it.getDistance(DistanceUnit.MM)
                } catch (_: Throwable) {
                    Double.NaN
                }
            } ?: Double.NaN
        }

        table.put("deviceName", _deviceName)
        table.put("connectionInfo", _connectionInfo)
        table.put("manufacturer", _manufacturer)
        table.put("version", _version)

        table.put("gain", _gain)
        table.put("distanceMm", _distanceMm)

        val colors = table.getSubtable("normalized")
        colors.put("red", _normalized.red.toDouble())
        colors.put("green", _normalized.green.toDouble())
        colors.put("blue", _normalized.blue.toDouble())
        colors.put("alpha", _normalized.alpha.toDouble())
    }

    override fun fromLog(table: LogTable) {
        _deviceName = table.get("deviceName", "MockNormalizedColorSensor")
        _connectionInfo = table.get("connectionInfo", "")
        _manufacturer = table.get("manufacturer", HardwareDevice.Manufacturer.Other)
        _version = table.get("version", 1)

        _gain = table.get("gain", 1.0f)
        _distanceMm = table.get("distanceMm", Double.NaN)

        val colors = table.getSubtable("normalized")
        val rgba = NormalizedRGBA()
        rgba.red = colors.get("red", 0.0).toFloat()
        rgba.green = colors.get("green", 0.0).toFloat()
        rgba.blue = colors.get("blue", 0.0).toFloat()
        rgba.alpha = colors.get("alpha", 0.0).toFloat()
        _normalized = rgba
    }

    // HardwareDevice
    override fun getManufacturer() = _manufacturer
    override fun getDeviceName() = _deviceName
    override fun getConnectionInfo() = _connectionInfo
    override fun getVersion() = _version
    override fun resetDeviceConfigurationForOpMode() {
        device?.resetDeviceConfigurationForOpMode()
    }

    override fun close() {
        device?.close()
    }

    // NormalizedColorSensor
    override fun getNormalizedColors(): NormalizedRGBA = _normalized

    override fun setGain(gain: Float) {
        _gain = gain
        try {
            device?.gain = gain
        } catch (_: Throwable) {
            // ignore
        }
    }

    override fun getGain(): Float = _gain

    // DistanceSensor (supported by RevColorSensorV3; safe fallback otherwise)
    override fun getDistance(unit: DistanceUnit): Double {
        return if (_distanceMm.isNaN()) Double.NaN else unit.fromMm(_distanceMm)
    }
}
