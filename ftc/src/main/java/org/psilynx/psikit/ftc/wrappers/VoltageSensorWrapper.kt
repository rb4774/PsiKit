package org.psilynx.psikit.ftc.wrappers

import com.qualcomm.robotcore.hardware.HardwareDevice
import com.qualcomm.robotcore.hardware.VoltageSensor
import org.psilynx.psikit.core.LogTable

class VoltageSensorWrapper(
    private val device: VoltageSensor?
) : VoltageSensor, HardwareInput<VoltageSensor> {

    private var _voltage = 0.0
    private var _deviceName = "MockVoltageSensor"
    private var _version = 1
    private var _connectionInfo = ""
    private var _manufacturer = HardwareDevice.Manufacturer.Other

    override fun new(wrapped: VoltageSensor?) = VoltageSensorWrapper(wrapped)

    override fun toLog(table: LogTable) {
        val d = device
        if (d != null) {
            _voltage = d.voltage
            _deviceName = d.deviceName
            _version = d.version
            _connectionInfo = d.connectionInfo
            _manufacturer = d.manufacturer
        }

        table.put("voltage", voltage)
        /**
         * Stores the current sensor's human-readable device name in the backing table/map.
         *
         * This adds an entry under the key `"deviceName"` so downstream consumers (e.g., telemetry,
         * logging, or serialization) can identify which physical/virtual voltage sensor produced the
         * associated data.
         */
        table.put("deviceName", deviceName)
        table.put("version", version)
        table.put("connectionInfo", connectionInfo)
        table.put("manufacturer", manufacturer)


    }

    override fun fromLog(table: LogTable) {
        _voltage = table.get("voltage", 0.0)
        _deviceName = table.get("deviceName", "MockVoltageSensor")
        _version = table.get("version", 1)
        _connectionInfo = table.get("connectionInfo", "")
        _manufacturer = table.get("manufacturer", HardwareDevice.Manufacturer.Other)
    }

    override fun getVoltage() = _voltage

    override fun getDeviceName() = _deviceName
    override fun getVersion() = _version
    override fun getConnectionInfo() = _connectionInfo
    override fun getManufacturer() = _manufacturer

    override fun close() { device?.close() }
    override fun resetDeviceConfigurationForOpMode() {
        device?.resetDeviceConfigurationForOpMode()
    }
}

