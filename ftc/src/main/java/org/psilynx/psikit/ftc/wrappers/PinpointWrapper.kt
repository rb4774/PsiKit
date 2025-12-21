package org.psilynx.psikit.ftc.wrappers

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D
import org.firstinspires.ftc.robotcore.external.navigation.Quaternion
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit
import org.psilynx.psikit.core.LogTable
import org.psilynx.psikit.ftc.GoBildaPinpointDriver
import org.psilynx.psikit.ftc.MockI2cDeviceSyncSimple
import kotlin.math.PI

class PinpointWrapper(val device: GoBildaPinpointDriver?):
    HardwareInput<GoBildaPinpointDriver>,
    GoBildaPinpointDriver(
        device?.deviceClient ?: MockI2cDeviceSyncSimple(), true
    )
{
    var _deviceID = 0
    var _deviceVersion = 0
    var _yawScalar = 0f
    var _deviceStatus = DeviceStatus.CALIBRATING
    var _loopTime = 0
    var _xEncoderValue = 0
    var _yEncoderValue = 0
    var _xPosition = 0.0
    var _yPosition = 0.0
    var _hOrientation = 0.0
    var _xVelocity = 0.0
    var _yVelocity = 0.0
    var _hVelocity = 0.0
    var _xOffset = 0f
    var _yOffset = 0f
    var _quaternionW = 0f
    var _quaternionX = 0f
    var _quaternionY = 0f
    var _quaternionZ = 0f
    var _pitch = 0.0
    var _roll = 0.0

    override fun new(wrapped: GoBildaPinpointDriver?) = PinpointWrapper(wrapped)

    override fun toLog(table: LogTable) {
        val d = device
        if (d != null) {
            _deviceID      = d.deviceID
            _deviceVersion = d.deviceVersion
            _yawScalar     = d.yawScalar
            _deviceStatus  = d.deviceStatus
            _xOffset       = d.getXOffset(DistanceUnit.MM)
            _yOffset       = d.getYOffset(DistanceUnit.MM)

            _xEncoderValue = d.encoderX
            _yEncoderValue = d.encoderY
            _loopTime      = d.loopTime
            _xPosition     = d.getPosX(DistanceUnit.MM)
            _yPosition     = d.getPosY(DistanceUnit.MM)
            _hOrientation  = d.getHeading(UnnormalizedAngleUnit.RADIANS)
            _xVelocity     = d.getVelX(DistanceUnit.MM)
            _yVelocity     = d.getVelY(DistanceUnit.MM)
            _hVelocity     = d.getHeadingVelocity(UnnormalizedAngleUnit.RADIANS)

            if (_deviceVersion > 1) {
                _quaternionW = d.quaternion.w
                _quaternionX = d.quaternion.x
                _quaternionY = d.quaternion.y
                _quaternionZ = d.quaternion.z
                _pitch = d.getPitch(AngleUnit.RADIANS)
                _roll  = d.getRoll(AngleUnit.RADIANS)
            }
        }

        table.put("deviceId", _deviceID)
        table.put("deviceVersion", _deviceVersion)
        table.put("yawScalar", _yawScalar)
        table.put("xOffset", _xOffset)
        table.put("yOffset", _yOffset)
        table.put("xEncoderValue", _xEncoderValue)
        table.put("yEncoderValue", _yEncoderValue)
        table.put("loopTime", _loopTime)
        table.put("deviceStatus", _deviceStatus)
        table.put("xPosition", _xPosition)
        table.put("yPosition", _yPosition)
        table.put("hOrientation", _hOrientation)
        table.put("xVelocity", _xVelocity)
        table.put("yVelocity", _yVelocity)
        table.put("hVelocity", _hVelocity)
        if (_deviceVersion > 1) {
            table.put("quaternionW", _quaternionW)
            table.put("quaternionX", _quaternionX)
            table.put("quaternionY", _quaternionY)
            table.put("quaternionZ", _quaternionZ)
            table.put("pitch", _pitch)
            table.put("roll", _roll)
        }
    }

    override fun fromLog(table: LogTable) {
        _deviceID      = table.get("deviceId", 0)
        _deviceVersion = table.get("deviceVersion", 0)
        _yawScalar     = table.get("yawScalar", 0f)
        _deviceStatus  = table.get("deviceStatus", DeviceStatus.CALIBRATING)
        _loopTime      = table.get("loopTime", 0)
        _xEncoderValue = table.get("xEncoderValue", 0)
        _yEncoderValue = table.get("yEncoderValue", 0)
        _xPosition     = table.get("xPosition", 0.0)
        _yPosition     = table.get("yPosition", 0.0)
        _hOrientation  = table.get("hOrientation", 0.0)
        _xVelocity     = table.get("xVelocity", 0.0)
        _yVelocity     = table.get("yVelocity", 0.0)
        _hVelocity     = table.get("hVelocity", 0.0)
        _xOffset       = table.get("xOffset", 0f)
        _yOffset       = table.get("yOffset", 0f)
        if (_deviceVersion > 1) {
            _quaternionW = table.get("quaternionW", 0f)
            _quaternionX = table.get("quaternionX", 0f)
            _quaternionY = table.get("quaternionY", 0f)
            _quaternionZ = table.get("quaternionZ", 0f)
            _pitch = table.get("pitch", 0.0)
            _roll  = table.get("roll", 0.0)
        }
    }

    override fun update(){ device?.update() }

    override fun readRegister(register: Register){
        device?.readRegister (register)
    }

    override fun setBulkReadScope(vararg registers: Register){
        device?.setBulkReadScope(*registers)
    }

    override fun setErrorDetectionType(e: ErrorDetectionType){
        device?.setErrorDetectionType(e)
    }

    override fun getDeviceID(): Int {
        return _deviceID
    }
    override fun getDeviceVersion(): Int {
        return _deviceVersion
    }
    override fun getYawScalar(): Float {
        return _yawScalar
    }
    override fun getDeviceStatus(): DeviceStatus {
        return _deviceStatus
    }
    override fun getLoopTime(): Int {
        return _loopTime
    }
    override fun getFrequency(): Double {
        return if (_loopTime != 0) {
            1000000.0 / _loopTime;
        } else {
            0.0;
        }
    }
    override fun getEncoderX(): Int {
        return _xEncoderValue
    }
    override fun getEncoderY(): Int {
        return _yEncoderValue
    }
    override fun getPosX(distanceUnit: DistanceUnit): Double {
        return distanceUnit.fromMm(_xPosition)
    }
    override fun getPosY(distanceUnit: DistanceUnit): Double {
        return distanceUnit.fromMm(_yPosition)
    }
    override fun getHeading(angleUnit: AngleUnit): Double {
        return angleUnit.fromRadians(
            ( _hOrientation + PI) % ( 2 * PI) - PI
        )
    }
    override fun getHeading(unnormalizedAngleUnit: UnnormalizedAngleUnit): Double {
        return unnormalizedAngleUnit.fromRadians(_hOrientation)
    }
    override fun getVelX(distanceUnit: DistanceUnit): Double {
        return distanceUnit.fromMm(_xVelocity)
    }
    override fun getVelY(distanceUnit: DistanceUnit): Double {
        return distanceUnit.fromMm(_yVelocity)
    }
    override fun getHeadingVelocity(
        unnormalizedAngleUnit: UnnormalizedAngleUnit
    ): Double {
        return unnormalizedAngleUnit.fromRadians(_hVelocity)
    }
    override fun getXOffset(distanceUnit: DistanceUnit): Float {
        return distanceUnit.fromMm(_xOffset.toDouble()).toFloat()
    }
    override fun getYOffset(distanceUnit: DistanceUnit): Float {
        return distanceUnit.fromMm(_yOffset.toDouble()).toFloat()
    }
    override fun getPosition() = Pose2D(
        DistanceUnit.MM, getPosX(DistanceUnit.MM), getPosY(DistanceUnit.MM),
        AngleUnit.RADIANS, getHeading(AngleUnit.RADIANS),
    )

    override fun getQuaternion(): Quaternion{
        if(deviceVersion < 2) throw RuntimeException(
            "Quaternion output is not supported on this device firmware"
        );
        return Quaternion(
            _quaternionW,
            _quaternionX,
            _quaternionY,
            _quaternionZ,
            0,
        )
    }
    override fun getPitch(angleUnit: AngleUnit): Double{
        if(deviceVersion < 2) throw RuntimeException(
            "IMU Pitch output is not supported on this device firmware"
        );
        return angleUnit.fromRadians(_pitch)
    }

    override fun getRoll(angleUnit: AngleUnit): Double{
        if(deviceVersion < 2) throw RuntimeException(
            "IMU Roll output is not supported on this device firmware"
        );
        return angleUnit.fromRadians(_roll)
    }

}
