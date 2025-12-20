package org.psilynx.psikit.ftc.wrappers

import com.qualcomm.hardware.limelightvision.LLResult
import com.qualcomm.hardware.limelightvision.LLStatus
import com.qualcomm.hardware.limelightvision.Limelight3A
import com.qualcomm.robotcore.hardware.HardwareDevice
import org.psilynx.psikit.core.LogTable

/**
 * Minimal Limelight3A logging:
 * - Status (name/temp/cpu/fps/pipeline)
 * - Latest result validity + common fields (tx/ty/latencies)
 *
 * This wrapper does NOT call start/stop or change pipeline; user code should do that.
 */
class Limelight3AWrapper(
    private val device: Limelight3A?,
) : HardwareInput<Limelight3A> {

    private var _deviceName = "MockLimelight3A"
    private var _connectionInfo = ""
    private var _manufacturer = HardwareDevice.Manufacturer.Other
    private var _version = 1

    private var _statusName = ""
    private var _tempC = Double.NaN
    private var _cpuPct = Double.NaN
    private var _fps = Double.NaN
    private var _pipelineIndex = -1
    private var _pipelineType = ""

    private var _hasResult = false
    private var _resultValid = false
    private var _tx = Double.NaN
    private var _ty = Double.NaN
    private var _txnc = Double.NaN
    private var _tync = Double.NaN
    private var _captureLatencyMs = Double.NaN
    private var _targetingLatencyMs = Double.NaN
    private var _parseLatencyMs = Double.NaN
    private var _pythonOutput = doubleArrayOf()
    private var _botposeString = ""

    private var _fiducialCount = 0
    private var _detectorCount = 0
    private var _classifierCount = 0
    private var _barcodeCount = 0
    private var _colorCount = 0

    override fun new(wrapped: Limelight3A?) = Limelight3AWrapper(wrapped)

    override fun toLog(table: LogTable) {
        val d = device
        if (d != null) {
            _deviceName = d.deviceName
            _connectionInfo = d.connectionInfo
            _manufacturer = d.manufacturer
            _version = d.version

            try {
                val status: LLStatus? = d.status
                if (status != null) {
                    _statusName = status.name ?: ""
                    _tempC = status.temp
                    _cpuPct = status.cpu
                    _fps = status.fps
                    _pipelineIndex = status.pipelineIndex
                    _pipelineType = status.pipelineType?.toString() ?: ""
                }
            } catch (_: Throwable) {
                // keep previous
            }

            try {
                val result: LLResult? = d.latestResult
                _hasResult = result != null
                if (result != null) {
                    _resultValid = result.isValid
                    _tx = result.tx
                    _ty = result.ty
                    _txnc = result.txNC
                    _tync = result.tyNC
                    _captureLatencyMs = result.captureLatency
                    _targetingLatencyMs = result.targetingLatency
                    _parseLatencyMs = result.parseLatency
                    _pythonOutput = try {
                        result.pythonOutput ?: doubleArrayOf()
                    } catch (_: Throwable) {
                        doubleArrayOf()
                    }
                    _botposeString = try {
                        result.botpose?.toString() ?: ""
                    } catch (_: Throwable) {
                        ""
                    }

                    _fiducialCount = safeCount { result.fiducialResults }
                    _detectorCount = safeCount { result.detectorResults }
                    _classifierCount = safeCount { result.classifierResults }
                    _barcodeCount = safeCount { result.barcodeResults }
                    _colorCount = safeCount { result.colorResults }
                }
            } catch (_: Throwable) {
                // keep previous
            }
        }

        table.put("deviceName", _deviceName)
        table.put("connectionInfo", _connectionInfo)
        table.put("manufacturer", _manufacturer)
        table.put("version", _version)

        val status = table.getSubtable("status")
        status.put("name", _statusName)
        status.put("tempC", _tempC)
        status.put("cpuPct", _cpuPct)
        status.put("fps", _fps)
        status.put("pipelineIndex", _pipelineIndex)
        status.put("pipelineType", _pipelineType)

        val result = table.getSubtable("result")
        result.put("hasResult", _hasResult)
        result.put("isValid", _resultValid)
        result.put("tx", _tx)
        result.put("ty", _ty)
        result.put("txnc", _txnc)
        result.put("tync", _tync)
        result.put("captureLatencyMs", _captureLatencyMs)
        result.put("targetingLatencyMs", _targetingLatencyMs)
        result.put("parseLatencyMs", _parseLatencyMs)
        result.put("pythonOutput", _pythonOutput)
        result.put("botpose", _botposeString)
        result.put("fiducialCount", _fiducialCount)
        result.put("detectorCount", _detectorCount)
        result.put("classifierCount", _classifierCount)
        result.put("barcodeCount", _barcodeCount)
        result.put("colorCount", _colorCount)
    }

    override fun fromLog(table: LogTable) {
        _deviceName = table.get("deviceName", "MockLimelight3A")
        _connectionInfo = table.get("connectionInfo", "")
        _manufacturer = table.get("manufacturer", HardwareDevice.Manufacturer.Other)
        _version = table.get("version", 1)

        val status = table.getSubtable("status")
        _statusName = status.get("name", "")
        _tempC = status.get("tempC", Double.NaN)
        _cpuPct = status.get("cpuPct", Double.NaN)
        _fps = status.get("fps", Double.NaN)
        _pipelineIndex = status.get("pipelineIndex", -1)
        _pipelineType = status.get("pipelineType", "")

        val result = table.getSubtable("result")
        _hasResult = result.get("hasResult", false)
        _resultValid = result.get("isValid", false)
        _tx = result.get("tx", Double.NaN)
        _ty = result.get("ty", Double.NaN)
        _txnc = result.get("txnc", Double.NaN)
        _tync = result.get("tync", Double.NaN)
        _captureLatencyMs = result.get("captureLatencyMs", Double.NaN)
        _targetingLatencyMs = result.get("targetingLatencyMs", Double.NaN)
        _parseLatencyMs = result.get("parseLatencyMs", Double.NaN)
        _pythonOutput = result.get("pythonOutput", doubleArrayOf())
        _botposeString = result.get("botpose", "")
        _fiducialCount = result.get("fiducialCount", 0)
        _detectorCount = result.get("detectorCount", 0)
        _classifierCount = result.get("classifierCount", 0)
        _barcodeCount = result.get("barcodeCount", 0)
        _colorCount = result.get("colorCount", 0)
    }

    private fun <T> safeCount(getter: () -> List<T>?): Int {
        return try {
            getter()?.size ?: 0
        } catch (_: Throwable) {
            0
        }
    }
}
