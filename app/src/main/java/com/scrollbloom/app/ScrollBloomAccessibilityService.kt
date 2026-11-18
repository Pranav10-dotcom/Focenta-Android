package com.scrollbloom.app

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.sin

class ScrollBloomAccessibilityService : AccessibilityService() {

    companion object {
        private const val OVERLAY_WIDTH = 140
        private const val OVERLAY_HEIGHT = 155
        private const val CHARACTER_SIZE = 125
        private const val OVERLAY_Y = 65
        private const val FADE_DELAY = 1800L
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: ScrollBloomRepository
    private lateinit var detector: InstagramDetector

    private var overlayWindow: WindowManager? = null
    private var overlayContainer: FrameLayout? = null
    private var plantView: PlantOverlayView? = null
    private var countView: TextView? = null
    private var isOverlayVisible = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = ScrollBloomRepository.get(this)
        detector = InstagramDetector()
        overlayWindow = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName != InstagramDetector.PACKAGE_NAME) {
            hideOverlay()
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val root = rootInActiveWindow
                if (root == null || !detector.isLikelyShortVideoScreen(root)) {
                    hideOverlay()
                    return
                }

                val result = detector.onScrolled(root)
                if (result != null) {
                    repository.recordShortVideo(
                        packageName = result.packageName,
                        confidence = result.confidence,
                        now = result.timestamp
                    )
                    showOverlay()
                    updateVisualState(react = true)
                } else {
                    showOverlay()
                    updateVisualState(react = false)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val root = rootInActiveWindow
                if (root != null && detector.isLikelyShortVideoScreen(root)) {
                    showOverlay()
                    updateVisualState(react = false)
                } else {
                    hideOverlay()
                }
            }
        }
    }

    private fun updateVisualState(react: Boolean) {
        if (!::repository.isInitialized) return
        val snapshot = repository.snapshot()
        plantView?.health = snapshot.health.toFloat()
        plantView?.recoveryActive = snapshot.recoveryActive
        if (react) plantView?.reactToScroll()
        updateCount(snapshot.dailyCount)
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        if (overlayContainer == null) createOverlay()

        overlayContainer?.visibility = View.VISIBLE
        overlayContainer?.alpha = 0.82f
        plantView?.setActive(true)
        isOverlayVisible = true
        updateVisualState(react = false)

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ fadeOverlay() }, FADE_DELAY)
    }

    private fun createOverlay() {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayWindow = windowManager

        overlayContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }

        plantView = PlantOverlayView(this)
        val plantParams = FrameLayout.LayoutParams(
            dpToPx(CHARACTER_SIZE),
            dpToPx(CHARACTER_SIZE)
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        overlayContainer?.addView(plantView, plantParams)

        countView = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val countParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dpToPx(25)
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(125)
        }
        overlayContainer?.addView(countView, countParams)

        val params = WindowManager.LayoutParams(
            dpToPx(OVERLAY_WIDTH),
            dpToPx(OVERLAY_HEIGHT),
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = dpToPx(OVERLAY_Y)
        params.alpha = 0.82f

        try {
            windowManager.addView(overlayContainer, params)
        } catch (_: Exception) {
            overlayContainer = null
            plantView = null
            countView = null
        }
    }

    private fun updateCount(count: Int) {
        countView?.text = count.toString()
    }

    private fun fadeOverlay() {
        if (!isOverlayVisible) return
        overlayContainer?.animate()
            ?.alpha(0.55f)
            ?.setDuration(500)
            ?.start()
    }

    private fun hideOverlay() {
        if (!isOverlayVisible) return
        handler.removeCallbacksAndMessages(null)
        plantView?.setActive(false)
        overlayContainer?.animate()
            ?.alpha(0f)
            ?.setDuration(180)
            ?.withEndAction {
                overlayContainer?.visibility = View.GONE
                isOverlayVisible = false
            }
            ?.start()
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        plantView?.setActive(false)
        try {
            overlayWindow?.removeView(overlayContainer)
        } catch (_: Exception) {
        }
        overlayContainer = null
        plantView = null
        countView = null
        super.onDestroy()
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private class PlantOverlayView(
        context: Context
    ) : View(context) {

        var health: Float = 100f
            set(value) {
                field = value.coerceIn(0f, 100f)
                invalidate()
            }

        var recoveryActive: Boolean = false
            set(value) {
                field = value
                invalidate()
            }

        private var reaction = 0f

        private val animator =
            ValueAnimator.ofFloat(
                0f,
                (Math.PI * 2).toFloat()
            ).apply {

                duration = 2200L

                repeatCount =
                    ValueAnimator.INFINITE

                addUpdateListener {

                    invalidate()
                }
            }

        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val flowerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {

            setLayerType(
                View.LAYER_TYPE_SOFTWARE,
                null
            )
        }

        fun setActive(active: Boolean) {
            if (active) {
                if (!animator.isRunning) animator.start()
            } else {
                if (animator.isStarted) animator.cancel()
            }
            invalidate()
        }

        fun reactToScroll() {

            reaction = 1f

            postDelayed({

                reaction = 0.65f
                invalidate()

            }, 120)

            postDelayed({

                reaction = 0f
                invalidate()

            }, 500)
        }

        override fun onDraw(
            canvas: Canvas
        ) {

            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()

            val centerX = w / 2f

            val time =
                animator.animatedValue as Float

            val breathing =
                sin(time) * 2.5f

            val bounce =
                reaction * 7f

            val health = this.health

            // -------------------------------------------------
            // Plant position
            // -------------------------------------------------

            val bodyCenterY =
                h * 0.57f -
                        bounce -
                        breathing

            val bodyWidth =
                w * 0.58f

            val bodyHeight =
                h * 0.40f

            // -------------------------------------------------
            // Health-based appearance
            // -------------------------------------------------

            val bodyColor = when {

                health >= 85 ->
                    Color.rgb(
                        166,
                        221,
                        143
                    )

                health >= 70 ->
                    Color.rgb(
                        158,
                        211,
                        135
                    )

                health >= 50 ->
                    Color.rgb(
                        181,
                        204,
                        117
                    )

                health >= 30 ->
                    Color.rgb(
                        202,
                        184,
                        104
                    )

                health >= 15 ->
                    Color.rgb(
                        181,
                        158,
                        100
                    )

                else ->
                    Color.rgb(
                        151,
                        143,
                        112
                    )
            }

            bodyPaint.color = bodyColor

            // -------------------------------------------------
            // Body
            // -------------------------------------------------

            val bodyRect = RectF(
                centerX - bodyWidth / 2f,
                bodyCenterY - bodyHeight / 2f,
                centerX + bodyWidth / 2f,
                bodyCenterY + bodyHeight / 2f
            )

            canvas.drawOval(
                bodyRect,
                bodyPaint
            )

            // -------------------------------------------------
            // Leaves
            // -------------------------------------------------

            leafPaint.color =
                Color.rgb(
                    102,
                    177,
                    105
                )

            val leafDroop = when {
                recoveryActive -> 0f
                health < 50 -> 12f
                else -> 0f
            }

            val leftLeaf =
                Path()

            leftLeaf.moveTo(
                centerX - bodyWidth * 0.43f,
                bodyCenterY - bodyHeight * 0.18f
            )

            leftLeaf.quadTo(
                centerX - bodyWidth * 0.72f,
                bodyCenterY - bodyHeight * 0.40f + leafDroop,
                centerX - bodyWidth * 0.52f,
                bodyCenterY + bodyHeight * 0.03f + leafDroop
            )

            leftLeaf.quadTo(
                centerX - bodyWidth * 0.40f,
                bodyCenterY - bodyHeight * 0.02f,
                centerX - bodyWidth * 0.43f,
                bodyCenterY - bodyHeight * 0.18f
            )

            canvas.drawPath(
                leftLeaf,
                leafPaint
            )

            val rightLeaf =
                Path()

            rightLeaf.moveTo(
                centerX + bodyWidth * 0.43f,
                bodyCenterY - bodyHeight * 0.18f
            )

            rightLeaf.quadTo(
                centerX + bodyWidth * 0.72f,
                bodyCenterY - bodyHeight * 0.40f + leafDroop,
                centerX + bodyWidth * 0.52f,
                bodyCenterY + bodyHeight * 0.03f + leafDroop
            )

            rightLeaf.quadTo(
                centerX + bodyWidth * 0.40f,
                bodyCenterY - bodyHeight * 0.02f,
                centerX + bodyWidth * 0.43f,
                bodyCenterY - bodyHeight * 0.18f
            )

            canvas.drawPath(
                rightLeaf,
                leafPaint
            )

            // -------------------------------------------------
            // Top leaf
            // -------------------------------------------------

            val topLeaf =
                Path()

            topLeaf.moveTo(
                centerX,
                bodyCenterY - bodyHeight * 0.38f
            )

            topLeaf.quadTo(
                centerX - bodyWidth * 0.08f,
                bodyCenterY - bodyHeight * 0.72f,
                centerX + bodyWidth * 0.16f,
                bodyCenterY - bodyHeight * 0.68f
            )

            topLeaf.quadTo(
                centerX + bodyWidth * 0.17f,
                bodyCenterY - bodyHeight * 0.45f,
                centerX,
                bodyCenterY - bodyHeight * 0.38f
            )

            canvas.drawPath(
                topLeaf,
                leafPaint
            )

            // -------------------------------------------------
            // Flower
            // -------------------------------------------------

            flowerPaint.color =
                Color.rgb(
                    245,
                    157,
                    190
                )

            val flowerX =
                centerX

            val flowerY =
                bodyCenterY -
                        bodyHeight * 0.48f

            val flowerSize =
                if (health > 30) 12f else 9f

            canvas.drawCircle(
                flowerX,
                flowerY - flowerSize * 0.6f,
                flowerSize * 0.55f,
                flowerPaint
            )

            canvas.drawCircle(
                flowerX - flowerSize * 0.55f,
                flowerY,
                flowerSize * 0.55f,
                flowerPaint
            )

            canvas.drawCircle(
                flowerX + flowerSize * 0.55f,
                flowerY,
                flowerSize * 0.55f,
                flowerPaint
            )

            canvas.drawCircle(
                flowerX,
                flowerY + flowerSize * 0.5f,
                flowerSize * 0.55f,
                flowerPaint
            )

            flowerPaint.color =
                Color.rgb(
                    255,
                    213,
                    92
                )

            canvas.drawCircle(
                flowerX,
                flowerY,
                flowerSize * 0.42f,
                flowerPaint
            )

            // -------------------------------------------------
            // Face
            // -------------------------------------------------

            eyePaint.color = Color.rgb(
                45,
                55,
                48
            )

            val eyeY =
                bodyCenterY -
                        bodyHeight * 0.05f

            val eyeSize =
                if (health > 30) 5.5f else 4.5f

            canvas.drawCircle(
                centerX - bodyWidth * 0.18f,
                eyeY,
                eyeSize,
                eyePaint
            )

            canvas.drawCircle(
                centerX + bodyWidth * 0.18f,
                eyeY,
                eyeSize,
                eyePaint
            )

            // -------------------------------------------------
            // Mouth
            // -------------------------------------------------

            mouthPaint.color =
                Color.rgb(
                    75,
                    69,
                    65
                )

            mouthPaint.style =
                Paint.Style.STROKE

            mouthPaint.strokeWidth = 3f

            val mouthRect =
                RectF(
                    centerX - 8f,
                    eyeY + 7f,
                    centerX + 8f,
                    eyeY + 20f
                )

            if (health >= 70) {

                canvas.drawArc(
                    mouthRect,
                    20f,
                    140f,
                    false,
                    mouthPaint
                )

            } else {

                canvas.drawArc(
                    mouthRect,
                    200f,
                    140f,
                    false,
                    mouthPaint
                )
            }

            mouthPaint.style =
                Paint.Style.FILL

            // -------------------------------------------------
            // Small feet
            // -------------------------------------------------

            bodyPaint.color =
                Color.rgb(
                    91,
                    143,
                    83
                )

            canvas.drawOval(
                RectF(
                    centerX - bodyWidth * 0.30f,
                    bodyCenterY + bodyHeight * 0.38f,
                    centerX - bodyWidth * 0.08f,
                    bodyCenterY + bodyHeight * 0.50f
                ),
                bodyPaint
            )

            canvas.drawOval(
                RectF(
                    centerX + bodyWidth * 0.08f,
                    bodyCenterY + bodyHeight * 0.38f,
                    centerX + bodyWidth * 0.30f,
                    bodyCenterY + bodyHeight * 0.50f
                ),
                bodyPaint
            )
        }

        override fun onDetachedFromWindow() {

            animator.cancel()

            super.onDetachedFromWindow()
        }
    }
}
