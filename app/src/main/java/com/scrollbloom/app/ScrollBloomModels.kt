package com.scrollbloom.app

/**
 * A minimal, derived event produced by the detection layer.
 * Raw accessibility nodes never leave the detector.
 */
data class DetectionResult(
    val packageName: String,
    val confidence: Float,
    val timestamp: Long
)

data class BehaviorSnapshot(
    val dailyCount: Int,
    val recentLoad: Float,
    val health: Int,
    val sessionActive: Boolean,
    val sessionDurationMs: Long,
    val sessionCount: Int,
    val reentryCount: Int,
    val longestBreakMs: Long,
    val recoveryActive: Boolean,
    val recoveryDurationMs: Long,
    val lastEventAt: Long
)

enum class PlantMood {
    HEALTHY,
    CONTENT,
    TIRED,
    THIRSTY,
    EXHAUSTED,
    DRAINED,
    RESTING,
    RECOVERING
}
