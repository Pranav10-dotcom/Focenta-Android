package com.scrollbloom.app

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Conservative Instagram short-video detector.
 * It is deliberately platform-specific so YouTube/Facebook detectors can be added later.
 */
class InstagramDetector {

    companion object {
        const val PACKAGE_NAME = "com.instagram.android"
        private const val CONFIRM_THRESHOLD = 0.75f
        private const val DUPLICATE_WINDOW_MS = 900L
        private const val MAX_HISTORY = 8
    }

    private var lastConfirmedAt = 0L
    private val recentCandidates = ArrayDeque<Long>(MAX_HISTORY)

    fun onScrolled(
        root: AccessibilityNodeInfo?,
        now: Long = System.currentTimeMillis()
    ): DetectionResult? {
        if (root == null) return null
        if (now - lastConfirmedAt < DUPLICATE_WINDOW_MS) return null

        val score = scoreRoot(root)
        if (score < CONFIRM_THRESHOLD) {
            rememberCandidate(now)
            return null
        }

        lastConfirmedAt = now
        rememberCandidate(now)
        return DetectionResult(PACKAGE_NAME, score, now)
    }

    fun isLikelyShortVideoScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return scoreRoot(root) >= 0.75f
    }

    private fun scoreRoot(root: AccessibilityNodeInfo): Float {
        var hasReels = false
        var hasAction = false
        var hasVideoSignal = false

        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val description = node.contentDescription?.toString()?.lowercase().orEmpty()
            val value = "$text $description"

            if (value.contains("reels") || value.contains("reel")) hasReels = true
            if (value.contains("like") || value.contains("comment") || value.contains("share")) hasAction = true
            if (value.contains("video") || value.contains("pause") || value.contains("play")) hasVideoSignal = true

            if (hasReels && hasAction) return 0.90f
            if (hasReels && hasVideoSignal) return 0.82f

            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }

        return when {
            hasReels && hasAction -> 0.90f
            hasReels && hasVideoSignal -> 0.82f
            hasReels -> 0.58f
            hasAction && hasVideoSignal -> 0.48f
            else -> 0f
        }
    }

    private fun rememberCandidate(timestamp: Long) {
        recentCandidates.addLast(timestamp)
        while (recentCandidates.size > MAX_HISTORY) recentCandidates.removeFirst()
    }
}
