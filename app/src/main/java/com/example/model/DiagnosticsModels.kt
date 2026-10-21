package com.example.model

data class AxisRangeInfo(
    val axisName: String,
    val axisId: Int,
    val min: Float,
    val max: Float,
    val flat: Float,
    val fuzz: Float
)

data class DiagnosticsState(
    val detectedInputs: Set<String> = emptySet(),
    val stuckButtons: Set<String> = emptySet(),
    val leftStickDriftOffset: Float = 0f,
    val rightStickDriftOffset: Float = 0f,
    val leftStickCenteringStatus: CenteringStatus = CenteringStatus.NEUTRAL,
    val rightStickCenteringStatus: CenteringStatus = CenteringStatus.NEUTRAL,
    val axisRanges: List<AxisRangeInfo> = emptyList(),
    val totalEventsCount: Long = 0L,
    val lastEventTime: Long = 0L,
    val vendorIdHex: String = "-",
    val productIdHex: String = "-",
    val inputSourcesDescription: String = "-",
    val hardwareNotes: List<String> = emptyList()
)

enum class CenteringStatus(val label: String) {
    NEUTRAL("Centered / Perfect"),
    SLIGHT_OFFSET("Minor Drift (Acceptable)"),
    SEVERE_DRIFT("High Drift / Off-Center"),
    ACTIVE_INPUT("Active (In Use)")
}
