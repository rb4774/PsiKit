package org.psilynx.psikit.ftc

import com.qualcomm.hardware.limelightvision.Limelight3A
import com.qualcomm.hardware.rev.RevColorSensorV3
import com.qualcomm.hardware.sparkfun.SparkFunOTOS
import com.qualcomm.robotcore.hardware.AccelerationSensor
import com.qualcomm.robotcore.hardware.AnalogInput
import com.qualcomm.robotcore.hardware.CRServo
import com.qualcomm.robotcore.hardware.ColorSensor
import com.qualcomm.robotcore.hardware.CompassSensor
import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.DcMotorController
import com.qualcomm.robotcore.hardware.DigitalChannel
import com.qualcomm.robotcore.hardware.GyroSensor
import com.qualcomm.robotcore.hardware.HardwareDevice
import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.I2cDevice
import com.qualcomm.robotcore.hardware.I2cDeviceSynch
import com.qualcomm.robotcore.hardware.IrSeekerSensor
import com.qualcomm.robotcore.hardware.LED
import com.qualcomm.robotcore.hardware.LightSensor
import com.qualcomm.robotcore.hardware.OpticalDistanceSensor
import com.qualcomm.robotcore.hardware.PWMOutput
import com.qualcomm.robotcore.hardware.NormalizedColorSensor
import com.qualcomm.robotcore.hardware.Servo
import com.qualcomm.robotcore.hardware.ServoController
import com.qualcomm.robotcore.hardware.TouchSensor
import com.qualcomm.robotcore.hardware.TouchSensorMultiplexer
import com.qualcomm.robotcore.hardware.UltrasonicSensor
import com.qualcomm.robotcore.hardware.VoltageSensor
import com.qualcomm.robotcore.util.SerialNumber
import org.psilynx.psikit.core.LogTable
import org.psilynx.psikit.core.LoggableInputs
import org.psilynx.psikit.core.Logger
import org.psilynx.psikit.ftc.wrappers.AnalogInputWrapper
import org.psilynx.psikit.ftc.wrappers.CrServoWrapper
import org.psilynx.psikit.ftc.wrappers.DigitalChannelWrapper
import org.psilynx.psikit.ftc.wrappers.HardwareInput
import org.psilynx.psikit.ftc.wrappers.Limelight3AWrapper
import org.psilynx.psikit.ftc.wrappers.MotorWrapper
import org.psilynx.psikit.ftc.wrappers.NormalizedColorSensorWrapper
import org.psilynx.psikit.ftc.wrappers.PinpointWrapper
import org.psilynx.psikit.ftc.wrappers.RevColorSensorV3Wrapper
import org.psilynx.psikit.ftc.wrappers.ServoWrapper
import org.psilynx.psikit.ftc.wrappers.SparkFunOTOSWrapper
import org.psilynx.psikit.ftc.wrappers.VoltageSensorWrapper
import java.util.SortedSet
import java.util.Spliterator
import java.util.function.Consumer

private class HardwareMapManifestInputs : LoggableInputs {
    data class Entry(
        val requestedTypes: MutableSet<String>,
    )

    private val entries = mutableMapOf<String, Entry>()

    fun declare(deviceName: String, requestedType: String) {
        val existing = entries[deviceName]
        if (existing == null) {
            entries[deviceName] = Entry(requestedTypes = mutableSetOf(requestedType))
            return
        }
        existing.requestedTypes.add(requestedType)
    }

    fun has(deviceName: String, requestedType: String): Boolean {
        return entries[deviceName]?.requestedTypes?.contains(requestedType) == true
    }

    fun namesForRequestedType(requestedType: String): List<String> {
        return entries
            .filterValues { it.requestedTypes.contains(requestedType) }
            .keys
            .sorted()
    }

    override fun toLog(table: LogTable) {
        table.put("schemaVersion", 1)

        val names = entries.keys.sorted().toTypedArray()
        table.put("names", names)

        for (name in names) {
            val entry = entries[name] ?: continue
            val sub = table.getSubtable(name)
            val types = entry.requestedTypes.toList().sorted().toTypedArray()
            // New format: multiple requested types per device.
            sub.put("requestedTypes", types)
            // Backward-compat convenience: keep writing a single requestedType when unambiguous.
            if (types.size == 1) {
                sub.put("requestedType", types[0])
            }
        }
    }

    override fun fromLog(table: LogTable) {
        entries.clear()

        val names = table.get("names", arrayOf<String>()).toList()
        val resolvedNames = if (names.isNotEmpty()) {
            names
        } else {
            // Backward/fallback: infer names from subtable keys.
            val inferred = mutableSetOf<String>()
            for (key in table.getAll(true).keys) {
                val firstSlash = key.indexOf('/')
                if (firstSlash > 0) {
                    inferred.add(key.substring(0, firstSlash))
                }
            }
            inferred.sorted()
        }

        for (name in resolvedNames) {
            val sub = table.getSubtable(name)
            val requestedTypes = sub.get("requestedTypes", arrayOf<String>()).toMutableSet()
            if (requestedTypes.isEmpty()) {
                // Backward compat: older logs wrote a single requestedType.
                val requestedType = sub.get("requestedType", "")
                if (requestedType.isNotBlank()) {
                    requestedTypes.add(requestedType)
                }
            }
            if (requestedTypes.isNotEmpty()) {
                entries[name] = Entry(requestedTypes = requestedTypes)
            }
        }
    }
}

class HardwareMapWrapper(
    val hardwareMap: HardwareMap?
): HardwareMap(
    hardwareMap?.appContext,
    null
){

    private val manifestInputs = HardwareMapManifestInputs()
    private val pendingReplayLookups = mutableListOf<Pair<String, String>>() // (requestedType, deviceName)

    init {
        // This map is global/static and used by logging loops to decide what to process.
        // Make each wrapper instance self-contained.
        devicesToProcess.clear()
        devicesToProcess[MANIFEST_DEVICE_KEY] = manifestInputs
    }

    private fun isReplayMode(): Boolean {
        return Logger.hasReplaySource() || Logger.isReplay() || hardwareMap == null
    }

    private fun recordLookupForManifest(classOrInterface: Class<*>?, deviceName: String) {
        val typeName = classOrInterface?.name ?: ""
        if (typeName.isNotBlank()) {
            manifestInputs.declare(deviceName, typeName)
        }
    }

    private fun ensureManifestLoadedAndValidatePendingIfPossible() {
        if (!isReplayMode()) {
            return
        }
        if (!Logger.isRunning()) {
            return
        }

        // Load the manifest from the log on replay.
        Logger.processInputs("HardwareMap/$MANIFEST_DEVICE_KEY", manifestInputs)

        if (pendingReplayLookups.isNotEmpty()) {
            for ((requestedType, deviceName) in pendingReplayLookups) {
                if (!manifestInputs.has(deviceName, requestedType)) {
                    error(
                        "Replay HardwareMap request not in manifest: " +
                            "get($requestedType, '$deviceName'). " +
                            "Run a real log with this device accessed at least once."
                    )
                }
            }
            pendingReplayLookups.clear()
        }
    }

    private fun requireManifestEntryIfPossible(classOrInterface: Class<*>?, deviceName: String) {
        if (!isReplayMode()) {
            return
        }

        val requestedType = classOrInterface?.name ?: ""
        if (requestedType.isBlank()) {
            error("Replay HardwareMap request has null/blank type for '$deviceName'")
        }

        if (!Logger.isRunning()) {
            // Can't read the log yet; validate as soon as the logger starts.
            pendingReplayLookups.add(requestedType to deviceName)
            return
        }

        ensureManifestLoadedAndValidatePendingIfPossible()
        if (!manifestInputs.has(deviceName, requestedType)) {
            error(
                "Replay HardwareMap request not in manifest: " +
                    "get($requestedType, '$deviceName'). " +
                    "Run a real log with this device accessed at least once."
            )
        }
    }
    /*
     * map of HardwareDevice classes to Inputs that wrap them. users should not
     * have to use this directly unless they are using an i2c device that
     * doesn't have support yet, in which case, they should look at the
     * PinpointInput as an example.
     */
    val deviceWrappers =
        mapOf<Class<out HardwareDevice>, HardwareInput<out HardwareDevice>>(
            GoBildaPinpointDriver::class.java to PinpointWrapper(null),

            // Vision + sensors
            Limelight3A::class.java            to Limelight3AWrapper(null),
            RevColorSensorV3::class.java       to RevColorSensorV3Wrapper(null),
            NormalizedColorSensor::class.java  to NormalizedColorSensorWrapper(null),

            DigitalChannel::class.java        to DigitalChannelWrapper(null),
            VoltageSensor::class.java         to VoltageSensorWrapper(null),
            SparkFunOTOS::class.java          to SparkFunOTOSWrapper(null),
            AnalogInput::class.java           to AnalogInputWrapper(null),
            CRServo::class.java               to CrServoWrapper(null),
            DcMotor::class.java               to MotorWrapper(null),
            Servo::class.java                 to ServoWrapper(null),
        )

    private fun findWrapperPrototypeForRequest(
        requestedClassOrInterface: Class<out Any?>?
    ): HardwareInput<HardwareDevice>? {
        if (requestedClassOrInterface == null) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val requestedHardwareDevice = requestedClassOrInterface as? Class<out HardwareDevice>
            ?: return null

        // Prefer direct match.
        @Suppress("UNCHECKED_CAST")
        val direct = deviceWrappers[requestedHardwareDevice] as? HardwareInput<HardwareDevice>
        if (direct != null) {
            return direct
        }

        // FTC code commonly requests more-specific SDK types (e.g., DcMotorEx).
        // If we have a wrapper registered for a supertype (e.g., DcMotor), use it.
        for ((wrapperKey, wrapperPrototype) in deviceWrappers) {
            if (wrapperKey.isAssignableFrom(requestedHardwareDevice)) {
                @Suppress("UNCHECKED_CAST")
                return wrapperPrototype as? HardwareInput<HardwareDevice>
            }
        }

        return null
    }
    /*
    init {
        this.allDeviceMappings.forEach { mapping ->
            this.getAll(mapping.deviceTypeClass)
            // this makes all the wrappers get put into the device mappings,
            // because each get calls the wrap command. this means that users
            // can use hardwaremap.<mapping> to get devices if they want to,
            // and they will still be wrapped
        }

    }
     */

    private fun <T : Any> wrap(
        classOrInterface: Class<out T?>?,
        name: String,
        device: T?
    ): T {
        if(device is HardwareInput<*>) return device
        if(device !is HardwareDevice && device != null) Logger.logCritical(
            "tried to get something from the hardwaremap that doesn't extend"
            + " HardwareDevice"
        )

        // this puts the device into the device mappings (real hardware only)
        if (device != null) {
            when (device) {
                is TouchSensorMultiplexer -> this.touchSensorMultiplexer.put(
                    name, device
                )
                is OpticalDistanceSensor -> this.opticalDistanceSensor.put(
                    name, device
                )
                is AccelerationSensor -> this.accelerationSensor.put(name, device)
                is DcMotorController -> this.dcMotorController.put(name, device)
                is UltrasonicSensor -> this.ultrasonicSensor.put(name, device)
                is ServoController -> this.servoController.put(name, device)
                is DigitalChannel -> this.digitalChannel.put(name, device)
                is IrSeekerSensor -> this.irSeekerSensor.put(name, device)
                is I2cDeviceSynch -> this.i2cDeviceSynch.put(name, device)
                is VoltageSensor -> this.voltageSensor.put(name, device)
                is CompassSensor -> this.compassSensor.put(name, device)
                is AnalogInput -> this.analogInput.put(name, device)
                is TouchSensor -> this.touchSensor.put(name, device)
                is ColorSensor -> this.colorSensor.put(name, device)
                is LightSensor -> this.lightSensor.put(name, device)
                is GyroSensor -> this.gyroSensor.put(name, device)
                is PWMOutput -> this.pwmOutput.put(name, device)
                is I2cDevice -> this.i2cDevice.put(name, device)
                is DcMotor -> this.dcMotor.put(name, device)
                is CRServo -> this.crservo.put(name, device)
                is Servo -> this.servo.put(name, device)
                is LED -> this.led.put(name, device)
                else -> {
                    Logger.logWarning(
                        "device type ${device.apply { this::class.qualifiedName }}" +
                            " not in all device mappings"
                    )
                }
            }
        }
        // Always record accessed devices for the manifest on real runs, even if
        // the type isn't currently supported by a wrapper.
        if (!isReplayMode()) {
            recordLookupForManifest(classOrInterface, name)
        }

        val deviceAsHardwareDevice = device as? HardwareDevice
        val wrapperPrototype = findWrapperPrototypeForRequest(classOrInterface)
        val wrapper = wrapperPrototype?.new(deviceAsHardwareDevice)

        if (wrapper != null) {
            // Always attach for logging.
            devicesToProcess[name] = wrapper

            // Return the wrapper only if it is assignable to the requested type.
            // Otherwise, keep returning the real SDK device and treat the wrapper as a "sidecar" logger.
            val canReturnWrapper = classOrInterface != null && classOrInterface.isInstance(wrapper)
            if (canReturnWrapper) {
                Logger.logInfo(
                    "hardwaremap call on $classOrInterface, returning wrapper ${wrapper.javaClass.canonicalName}"
                )
                @Suppress("UNCHECKED_CAST")
                return wrapper as T
            }

            if (device != null) {
                Logger.logInfo(
                    "hardwaremap call on $classOrInterface, attached inputs logger ${wrapper.javaClass.canonicalName} (returning SDK device)"
                )
                return device
            }

            // Replay mode: we can't return a real device instance, and the wrapper doesn't implement the requested type.
            error(
                "Replay cannot provide a '${classOrInterface?.name}' instance for '$name'. " +
                    "PsiKit attached inputs logger ${wrapper.javaClass.canonicalName}, but the returned type must be compatible. " +
                    "Use a supported interface type (if available) or add a full wrapper that implements the requested type."
            )
        }

        Logger.logInfo("hardwaremap call on $classOrInterface, got wrapper null")
        if (device != null) return device
        else {
            Logger.logCritical(
                "device to wrap is null, and no wrapper can be found." +
                " exiting with error"
            )
            error("")
        }
    }

    override fun <T : Any> get(
        classOrInterface: Class<out T?>?,
        deviceName: String
    ) = wrap(
        classOrInterface,
        deviceName,
        if (isReplayMode()) {
            requireManifestEntryIfPossible(classOrInterface, deviceName)
            null
        } else {
            hardwareMap?.get<T>(classOrInterface, deviceName)
        }
    )

    override fun <T : Any> getAll(classOrInterface: Class<out T>): List<T> {

        if (isReplayMode()) {
            ensureManifestLoadedAndValidatePendingIfPossible()
            if (!Logger.isRunning()) {
                // Can't read the log yet.
                return listOf()
            }
            val names = manifestInputs.namesForRequestedType(classOrInterface.name)
            return names.map { name ->
                @Suppress("UNCHECKED_CAST")
                wrap(classOrInterface, name, null) as T
            }
        }

        Logger.logWarning(
            "HardwareMap.getAll(${classOrInterface.name}) may not be deterministic on real hardware. " +
                "Prefer get(Class, name) when possible, or sort by device name after discovery."
        )
        return hardwareMap?.getAll(classOrInterface)?.map {
            val name = getNamesOf(it as HardwareDevice).first()
            if (name == null) {
                Logger.logError(
                    "couldn't get a name for ${it::class.qualifiedName}"
                )
            }
            wrap(
                classOrInterface,
                name ?: "None",
                it
            )
        } ?: listOf()
    }

    override fun get(deviceName: String): HardwareDevice? {
        Logger.logError(
            "method get (without a class) not wrapped correctly, it is very "
            + "likely that using this will break determinism"
        )

        val device = hardwareMap?.get(deviceName)
        if(device == null) return null

        return wrap(device::class.java, deviceName, device)
    }

    override fun forEach(action: Consumer<in HardwareDevice>) {
        hardwareMap?.forEach(action)
    }

    override fun spliterator(): Spliterator<HardwareDevice?> {
        Logger.logError(
            "method spliterator not wrapped correctly, it is very "
            + "likely that using this will break determinism"
            + " I'm gonna be real, I have no idea what a \"Spliterator\" is "
            + "or why I should waste my time implementing it"
        )
        if(hardwareMap == null) error(
            "okay you can't even get the spliterator in replay"
        )
        return hardwareMap.spliterator()
    }

    override fun getAllNames(classOrInterface: Class<out HardwareDevice?>?): SortedSet<String?>? {
        Logger.logError(
            "method getAllNames not wrapped correctly, it is very "
            + "likely that using this will break determinism"
        )
        return hardwareMap?.getAllNames(classOrInterface) ?: sortedSetOf()
    }

    override fun getNamesOf(device: HardwareDevice?): Set<String?> {
        Logger.logInfo(
            "HardwareMap.getNamesOf(...) used; this may not be fully deterministic on real hardware."
        )
        return hardwareMap?.getNamesOf(device) ?: setOf(device?.deviceName)
    }

    override fun <T : Any?> get(
        classOrInterface: Class<out T?>?,
        serialNumber: SerialNumber?
    ): T {

        if (isReplayMode()) {
            // SerialNumber-based lookup can't be reproduced deterministically without a
            // serial-number manifest. Keep behavior strict.
            error(
                "Replay does not support HardwareMap.get(Class, SerialNumber). " +
                    "Use get(Class, name) so the manifest can be enforced."
            )
        }

        val device = hardwareMap?.get(classOrInterface, serialNumber)
        val name = getNamesOf(device as? HardwareDevice).first()
        if (name == null) {
            Logger.logError(
                "couldn't get a name for ${
                    device?.apply { this::class.qualifiedName }
                }"
            )
        }
        return wrap(
            classOrInterface,
            name ?: "None",
            device
        )
    }

    override fun iterator(): MutableIterator<HardwareDevice?> {
        Logger.logError(
            "method iterator not wrapped correctly, it is very "
            + "likely that using this will break determinism"
        )
        if(hardwareMap == null) error(
            "okay you can't even get the iterator in replay"
        )
        return hardwareMap.iterator()
    }

    override fun toString(): String {
        return hardwareMap?.toString() ?: super.toString()
    }

    override fun <T : Any> tryGet(
        classOrInterface: Class<out T>,
        deviceName: String
    ): T? {
        if (isReplayMode()) {
            requireManifestEntryIfPossible(classOrInterface, deviceName)
            @Suppress("UNCHECKED_CAST")
            return wrap(classOrInterface, deviceName, null) as T?
        }

        val device = hardwareMap?.tryGet<T>(classOrInterface, deviceName)
        return (
            if (hardwareMap == null || device != null) {
                @Suppress("UNCHECKED_CAST")
                wrap(
                    classOrInterface,
                    deviceName,
                    device
                ) as T?
            } else {
                null
            }
        )
    }

    companion object {
        private const val MANIFEST_DEVICE_KEY = "_manifest"
        internal val devicesToProcess = mutableMapOf<String, LoggableInputs>()
    }
}