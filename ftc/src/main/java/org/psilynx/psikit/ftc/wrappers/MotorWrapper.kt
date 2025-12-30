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

    companion object {
        /**
         * Motor velocity reads can be expensive on some hub/SDK combos.
         * Disable if you're seeing high `PsiKit/logTimes` for motors.
         */
        @JvmStatic var logVelocity: Boolean = true

        /**
         * If non-empty, velocity is only logged for motors whose HardwareMap name matches.
         * Example: set to {"fly_left", "fly_right"}.
         */
        @JvmStatic var velocityMotorNames: Set<String> = emptySet()

        /**
         * If non-empty (and [velocityMotorNames] is empty), velocity is only logged when the
         * motor name starts with one of these prefixes.
         */
        @JvmStatic var velocityMotorNamePrefixes: Set<String> = emptySet()

        @JvmStatic fun setVelocityLoggedMotors(vararg names: String) {
            velocityMotorNames = names.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }

        @JvmStatic fun setVelocityLoggedMotorPrefixes(vararg prefixes: String) {
            velocityMotorNamePrefixes = prefixes.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }

        /**
         * Over-current status can be expensive; default off.
         */
        @JvmStatic var logOverCurrent: Boolean = false

        /**
         * Busy reads can be expensive depending on mode/controller; default on.
         */
        @JvmStatic var logBusy: Boolean = true

        /**
         * Metadata fields are effectively static; refresh them rarely.
         */
        @JvmStatic var metadataRefreshPeriodSec: Double = 2.0

        /**
         * Configuration-ish fields rarely change; refresh periodically.
         */
        @JvmStatic var configRefreshPeriodSec: Double = 0.25
    }

    internal var psikitName: String = ""

    private var _zeroPowerBehavior = DcMotor.ZeroPowerBehavior.UNKNOWN
    private var _powerFloat  = false
    private var _overCurrent = false
    private var _currentPos  = 0
    private var _currentVel  = 0.0
    private var _targetVelTps = 0.0
    private var _targetVelAngular = 0.0
    private var _targetVelUnit: AngleUnit? = null
    private var _power       = 0.0
    private var _direction   = DcMotorSimple.Direction.FORWARD
    private var _mode        = DcMotor.RunMode.RUN_WITHOUT_ENCODER
    private var _targetPos   = 0
    private var _busy        = false
    private var _deviceName  = "MockMotor"
    private var _version     = 1
    private var _connectionInfo = ""
    private var _manufacturer   = HardwareDevice.Manufacturer.Other

    private var lastMetadataUpdateNs: Long = Long.MIN_VALUE
    private var lastConfigUpdateNs: Long = Long.MIN_VALUE

    private fun shouldLogVelocity(): Boolean {
        if (!logVelocity) return false

        val name = psikitName
        if (velocityMotorNames.isNotEmpty()) {
            return velocityMotorNames.contains(name)
        }

        if (velocityMotorNamePrefixes.isNotEmpty()) {
            return velocityMotorNamePrefixes.any { prefix -> name.startsWith(prefix) }
        }

        return true
    }

    private fun secondsSince(ns: Long): Double {
        if (ns == Long.MIN_VALUE) return Double.POSITIVE_INFINITY
        return (System.nanoTime() - ns) / 1_000_000_000.0
    }

    override fun new(wrapped: DcMotorImplEx?) = MotorWrapper(wrapped)

    override fun toLog(table: LogTable) {
        device!!

        // Static-ish metadata: cache heavily.
        if (secondsSince(lastMetadataUpdateNs) >= metadataRefreshPeriodSec) {
            lastMetadataUpdateNs = System.nanoTime()
            _deviceName        = device.deviceName
            _version           = device.version
            _connectionInfo    = device.connectionInfo
            _manufacturer      = device.manufacturer
        }

        // Configuration-ish fields: refresh periodically (not every loop).
        if (secondsSince(lastConfigUpdateNs) >= configRefreshPeriodSec) {
            lastConfigUpdateNs = System.nanoTime()
            _zeroPowerBehavior = device.zeroPowerBehavior
            _powerFloat        = device.powerFloat
        }

        _overCurrent       = if (logOverCurrent) device.isOverCurrent else false
        _currentPos        = device.currentPosition
        _currentVel        = if (shouldLogVelocity()) device.velocity else 0.0
        _power             = device.power
        _direction         = device.direction
        _mode              = device.mode
        _targetPos         = device.targetPosition
        _busy              = if (logBusy) device.isBusy else false

        table.put("zeroPowerBehavior", _zeroPowerBehavior)
        table.put("powerFloat", _powerFloat)
        table.put("overCurrent", _overCurrent)
        table.put("currentPos", _currentPos)
        table.put("currentVel", _currentVel)
        table.put("targetVelTps", _targetVelTps)
        table.put("targetVelAngular", _targetVelAngular)
        table.put("targetVelUnit", _targetVelUnit?.name ?: "")
        table.put("power", _power)
        table.put("direction", _direction.name)
        table.put("mode", _mode.name)
        table.put("targetPos", _targetPos)
        table.put("busy", _busy)
        table.put("deviceName", _deviceName)
        table.put("version", _version)
        table.put("connectionInfo", _connectionInfo)
        table.put("manufacturer", _manufacturer)

    }

    override fun fromLog(table: LogTable) {
        _zeroPowerBehavior = table.get("zeroPowerBehavior", DcMotor.ZeroPowerBehavior.UNKNOWN)
        _powerFloat        = table.get("powerFloat", false)
        _overCurrent       = table.get("overCurrent", false)
        _currentPos        = table.get("currentPos", 0)
        _currentVel        = table.get("currentVel", 0.0)
        _targetVelTps      = table.get("targetVelTps", 0.0)
        _targetVelAngular  = table.get("targetVelAngular", 0.0)
        _targetVelUnit     = table.get("targetVelUnit", "").let { if (it.isEmpty()) null else AngleUnit.valueOf(it) }
        _power             = table.get("power", 0.0)
        _direction         = DcMotorSimple.Direction.valueOf(table.get("direction", DcMotorSimple.Direction.FORWARD.name))
        _mode              = DcMotor.RunMode.valueOf(table.get("mode", DcMotor.RunMode.RUN_WITHOUT_ENCODER.name))
        _targetPos         = table.get("targetPos", 0)
        _busy              = table.get("busy", false)
        _deviceName        = table.get("deviceName", "MockMotor")
        _version           = table.get("version", 1)
        _connectionInfo    = table.get("connectionInfo", "")
        _manufacturer      = table.get("manufacturer", HardwareDevice.Manufacturer.Other)
    }

    override fun getZeroPowerBehavior() = device?.zeroPowerBehavior ?: _zeroPowerBehavior
    override fun setZeroPowerBehavior(zeroPowerBehavior: DcMotor.ZeroPowerBehavior?) {
        val resolved = zeroPowerBehavior ?: DcMotor.ZeroPowerBehavior.UNKNOWN
        _zeroPowerBehavior = resolved
        if (device != null) {
            device.zeroPowerBehavior = resolved
        } else {
            super.setZeroPowerBehavior(resolved)
        }
    }

    override fun getPowerFloat() = device?.powerFloat ?: _powerFloat
    override fun getCurrentPosition() = device?.currentPosition ?: _currentPos
    override fun getVelocity() = device?.velocity ?: _currentVel
    override fun getVelocity(unit: AngleUnit?) = device?.getVelocity(unit) ?: _currentVel
    override fun getPower() = device?.power ?: _power
    override fun isOverCurrent() = device?.isOverCurrent ?: _overCurrent

    override fun setMode(mode: DcMotor.RunMode?) {
        val resolved = mode ?: DcMotor.RunMode.RUN_WITHOUT_ENCODER
        _mode = resolved
        if (device != null) {
            device.mode = resolved
        } else {
            super.setMode(resolved)
        }
    }

    override fun getMode(): DcMotor.RunMode {
        return device?.mode ?: _mode
    }

    override fun setTargetPosition(position: Int) {
        _targetPos = position
        if (device != null) {
            device.targetPosition = position
        } else {
            super.setTargetPosition(position)
        }
    }

    override fun getTargetPosition(): Int {
        return device?.targetPosition ?: _targetPos
    }

    override fun isBusy(): Boolean {
        return device?.isBusy ?: _busy
    }

    override fun setVelocity(ticksPerSecond: Double) {
        _targetVelTps = ticksPerSecond
        if (device != null) {
            device.velocity = ticksPerSecond
        } else {
            super.setVelocity(ticksPerSecond)
        }
    }

    override fun setVelocity(angularRate: Double, unit: AngleUnit?) {
        _targetVelAngular = angularRate
        _targetVelUnit = unit
        if (device != null) {
            device.setVelocity(angularRate, unit)
        } else {
            super.setVelocity(angularRate, unit)
        }
    }

    override fun setDirection(direction: DcMotorSimple.Direction?) {
        val resolved = direction ?: DcMotorSimple.Direction.FORWARD
        _direction = resolved
        if (device != null) {
            device.setDirection(resolved)
        } else {
            super.setDirection(resolved)
        }
    }

    override fun getDirection(): DcMotorSimple.Direction {
        return device?.direction ?: _direction
    }


    override fun getDeviceName() = _deviceName
    override fun getVersion() = _version
    override fun getConnectionInfo() = _connectionInfo
    override fun getManufacturer() = _manufacturer

    override fun setPower(power: Double) {
        _power = power
        if (device != null) {
            device.power = power
        } else {
            super.setPower(power)
        }
    }

    override fun close() { device?.close() }

    override fun resetDeviceConfigurationForOpMode() {
        device?.resetDeviceConfigurationForOpMode()
    }
}