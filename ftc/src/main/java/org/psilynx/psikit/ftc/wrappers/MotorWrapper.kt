package org.psilynx.psikit.ftc.wrappers

import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.DcMotorControllerEx
import com.qualcomm.robotcore.hardware.DcMotorImplEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.qualcomm.robotcore.hardware.HardwareDevice
import com.qualcomm.robotcore.hardware.PIDCoefficients
import com.qualcomm.robotcore.hardware.PIDFCoefficients
import com.qualcomm.robotcore.hardware.configuration.typecontainers.MotorConfigurationType
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit
import org.psilynx.psikit.core.LogTable

class MotorWrapper(
    private val device: DcMotorImplEx?
) : DcMotorImplEx(
    object : DcMotorControllerEx {
        override fun setMotorType(motor: Int, motorType: MotorConfigurationType?) {}
        override fun getMotorType(motor: Int) = MotorConfigurationType.getUnspecifiedMotorType()
        override fun setMotorMode(motor: Int, mode: DcMotor.RunMode?) {}
        override fun getMotorMode(motor: Int) = DcMotor.RunMode.RUN_WITHOUT_ENCODER
        override fun setMotorPower(motor: Int, power: Double) {}
        override fun getMotorPower(motor: Int) = 0.0
        override fun isBusy(motor: Int) = false
        override fun setMotorZeroPowerBehavior(motor: Int, zeroPowerBehavior: DcMotor.ZeroPowerBehavior?) {}
        override fun getMotorZeroPowerBehavior(motor: Int) = DcMotor.ZeroPowerBehavior.UNKNOWN
        override fun getMotorPowerFloat(motor: Int) = false
        override fun setMotorTargetPosition(motor: Int, position: Int) {}
        override fun getMotorTargetPosition(motor: Int) = 0
        override fun getMotorCurrentPosition(motor: Int) = 0
        override fun resetDeviceConfigurationForOpMode(motor: Int) {}
        override fun getManufacturer() = HardwareDevice.Manufacturer.Other
        override fun getDeviceName() = "MockMotor"
        override fun getConnectionInfo() = ""
        override fun getVersion() = 1
        override fun resetDeviceConfigurationForOpMode() {}
        override fun close() {}
        override fun setMotorEnable(motor: Int) {}
        override fun setMotorDisable(motor: Int) {}
        override fun isMotorEnabled(motor: Int) = false
        override fun setMotorVelocity(motor: Int, ticksPerSecond: Double) {}
        override fun setMotorVelocity(motor: Int, angularRate: Double, unit: AngleUnit?) {}
        override fun getMotorVelocity(motor: Int) = 0.0
        override fun getMotorVelocity(motor: Int, unit: AngleUnit?) = 0.0
        override fun setPIDCoefficients(motor: Int, mode: DcMotor.RunMode?, pidCoefficients: PIDCoefficients?) {}
        override fun setPIDFCoefficients(motor: Int, mode: DcMotor.RunMode?, pidfCoefficients: PIDFCoefficients?) {}
        override fun getPIDCoefficients(motor: Int, mode: DcMotor.RunMode?) = PIDCoefficients()
        override fun getPIDFCoefficients(motor: Int, mode: DcMotor.RunMode?) = PIDFCoefficients()
        override fun setMotorTargetPosition(motor: Int, position: Int, tolerance: Int) {}
        override fun getMotorCurrent(motor: Int, unit: CurrentUnit?) = 0.0
        override fun getMotorCurrentAlert(motor: Int, unit: CurrentUnit?) = 0.0
        override fun setMotorCurrentAlert(motor: Int, current: Double, unit: CurrentUnit?) {}
        override fun isMotorOverCurrent(motor: Int) = false
    },
    0,
    DcMotorSimple.Direction.FORWARD,
    MotorConfigurationType()
), HardwareInput<DcMotorImplEx> {

    private var _zeroPowerBehavior = DcMotor.ZeroPowerBehavior.UNKNOWN
    private var _powerFloat  = false
    private var _overCurrent = false
    private var _currentAmps = 0.0
    private var _currentAlertAmps = 0.0
    private var _lastCurrentReadNs = 0L
    private var _currentPos  = 0
    private var _currentVel  = 0.0
    private var _power       = 0.0
    private var _deviceName  = "MockMotor"
    private var _version     = 1
    private var _connectionInfo = ""
    private var _manufacturer   = HardwareDevice.Manufacturer.Other

    private fun shouldReadCurrentNow(): Boolean {
        // Reading motor current can be slow on some hubs/SDKs. Rate-limit while still logging.
        val now = System.nanoTime()
        val last = _lastCurrentReadNs
        if (last == 0L || (now - last) >= 50_000_000L) { // 0.05s
            _lastCurrentReadNs = now
            return true
        }
        return false
    }

    override fun new(wrapped: DcMotorImplEx?) = MotorWrapper(wrapped)

    override fun toLog(table: LogTable) {
        val d = device
        if (d != null) {
            _zeroPowerBehavior = d.zeroPowerBehavior
            _powerFloat        = d.powerFloat
            _overCurrent       = d.isOverCurrent
            _currentPos        = d.currentPosition
            _currentVel        = d.velocity
            _power             = d.power
            _deviceName        = d.deviceName
            _version           = d.version
            _connectionInfo    = d.connectionInfo
            _manufacturer      = d.manufacturer

            if (shouldReadCurrentNow()) {
                _currentAmps = try {
                    d.getCurrent(CurrentUnit.AMPS)
                } catch (_: Throwable) {
                    _currentAmps
                }

                _currentAlertAmps = try {
                    d.getCurrentAlert(CurrentUnit.AMPS)
                } catch (_: Throwable) {
                    _currentAlertAmps
                }
            }
        }

        table.put("zeroPowerBehavior", _zeroPowerBehavior)
        table.put("powerFloat", _powerFloat)
        table.put("overCurrent", _overCurrent)
        table.put("currentAmps", _currentAmps)
        table.put("currentAlertAmps", _currentAlertAmps)
        table.put("currentPos", _currentPos)
        table.put("currentVel", _currentVel)
        table.put("power", _power)
        table.put("deviceName", _deviceName)
        table.put("version", _version)
        table.put("connectionInfo", _connectionInfo)
        table.put("manufacturer", _manufacturer)

    }

    override fun fromLog(table: LogTable) {
        _zeroPowerBehavior = table.get("zeroPowerBehavior", DcMotor.ZeroPowerBehavior.UNKNOWN)
        _powerFloat        = table.get("powerFloat", false)
        _overCurrent       = table.get("overCurrent", false)
        _currentAmps        = table.get("currentAmps", 0.0)
        _currentAlertAmps   = table.get("currentAlertAmps", 0.0)
        _currentPos        = table.get("currentPos", 0)
        _currentVel        = table.get("currentVel", 0.0)
        _power             = table.get("power", 0.0)
        _deviceName        = table.get("deviceName", "MockMotor")
        _version           = table.get("version", 1)
        _connectionInfo    = table.get("connectionInfo", "")
        _manufacturer      = table.get("manufacturer", HardwareDevice.Manufacturer.Other)
    }

    override fun getZeroPowerBehavior() = device?.zeroPowerBehavior ?: _zeroPowerBehavior
    override fun setZeroPowerBehavior(zeroPowerBehavior: DcMotor.ZeroPowerBehavior?) {
        if (zeroPowerBehavior != null) {
            _zeroPowerBehavior = zeroPowerBehavior
        }
        device?.setZeroPowerBehavior(zeroPowerBehavior)
    }

    override fun getPowerFloat() = device?.powerFloat ?: _powerFloat
    override fun getCurrentPosition() = device?.currentPosition ?: _currentPos
    override fun getVelocity() = device?.velocity ?: _currentVel
    override fun getPower() = device?.power ?: _power
    override fun isOverCurrent() = device?.isOverCurrent ?: _overCurrent

    override fun getCurrent(unit: CurrentUnit): Double {
        val d = device
        if (d != null) {
            return try {
                d.getCurrent(unit)
            } catch (_: Throwable) {
                0.0
            }
        }
        return when (unit) {
            CurrentUnit.AMPS -> _currentAmps
            CurrentUnit.MILLIAMPS -> _currentAmps * 1000.0
        }
    }

    override fun getCurrentAlert(unit: CurrentUnit): Double {
        val d = device
        if (d != null) {
            return try {
                d.getCurrentAlert(unit)
            } catch (_: Throwable) {
                0.0
            }
        }
        return when (unit) {
            CurrentUnit.AMPS -> _currentAlertAmps
            CurrentUnit.MILLIAMPS -> _currentAlertAmps * 1000.0
        }
    }

    override fun setCurrentAlert(current: Double, unit: CurrentUnit) {
        val amps = when (unit) {
            CurrentUnit.AMPS -> current
            CurrentUnit.MILLIAMPS -> current / 1000.0
        }
        _currentAlertAmps = amps
        try {
            device?.setCurrentAlert(current, unit)
        } catch (_: Throwable) {
            // ignore
        }
    }


    override fun getDeviceName() = device?.deviceName ?: _deviceName
    override fun getVersion() = device?.version ?: _version
    override fun getConnectionInfo() = device?.connectionInfo ?: _connectionInfo
    override fun getManufacturer() = device?.manufacturer ?: _manufacturer

    override fun setDirection(direction: DcMotorSimple.Direction) {
        val d = device
        if (d != null) {
            d.direction = direction
        } else {
            super.setDirection(direction)
        }
    }

    override fun getDirection(): DcMotorSimple.Direction {
        val d = device
        return d?.direction ?: super.direction
    }

    override fun setMode(mode: DcMotor.RunMode) {
        val d = device
        if (d != null) {
            d.mode = mode
        } else {
            super.setMode(mode)
        }
    }

    override fun getMode(): DcMotor.RunMode {
        val d = device
        return d?.mode ?: super.getMode()
    }

    override fun setTargetPosition(position: Int) {
        val d = device
        if (d != null) {
            d.targetPosition = position
        } else {
            super.setTargetPosition(position)
        }
    }

    override fun getTargetPosition(): Int {
        val d = device
        return d?.targetPosition ?: super.getTargetPosition()
    }

    override fun isBusy(): Boolean {
        val d = device
        return d?.isBusy ?: super.isBusy()
    }

    override fun setPower(power: Double) {
        _power = power
        device?.setPower(power)
    }

    override fun close() { device?.close() }

    override fun resetDeviceConfigurationForOpMode() {
        device?.resetDeviceConfigurationForOpMode()
    }
}