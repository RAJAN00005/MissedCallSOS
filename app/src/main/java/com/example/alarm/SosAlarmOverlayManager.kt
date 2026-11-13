package com.example.alarm

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.data.preferences.AppPreferences
import com.example.service.SosAlarmService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Production-grade System Overlay Manager (TYPE_APPLICATION_OVERLAY) that displays
 * the full-screen animated Emergency SOS Alarm dismissal screen directly over the
 * lockscreen, incoming call screen, or any app with 0ms delay.
 *
 * Uses pure theme-independent Android Views (FrameLayout, LinearLayout, TextView, custom touch slider)
 * so it never fails on any OEM ROM (Xiaomi MIUI/HyperOS, Realme, Oppo, Vivo, Samsung, Pixel).
 */
object SosAlarmOverlayManager {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var pulseAnimator: ValueAnimator? = null

    @Volatile
    var isShowing: Boolean = false
        private set

    fun show(
        context: Context,
        callerName: String,
        callerNumber: String,
        missedCount: Int
    ) {
        val appContext = context.applicationContext
        mainHandler.post {
            try {
                if (!Settings.canDrawOverlays(appContext)) {
                    return@post
                }

                // If already showing, keep it visible rather than flickering
                if (isShowing && overlayView != null) {
                    return@post
                }

                dismissInternal()

                val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                windowManager = wm

                val preferences = AppPreferences(appContext)
                val pinRequired = preferences.isPinRequired()
                val correctPin = preferences.getAlarmPin()

                val root = buildOverlayView(
                    context = appContext,
                    callerName = callerName,
                    callerNumber = callerNumber,
                    missedCount = missedCount,
                    pinRequired = pinRequired,
                    correctPin = correctPin,
                    onStopAlarm = {
                        dismiss()
                        SosAlarmService.stop(appContext)
                    },
                    onCallBack = {
                        dismiss()
                        SosAlarmService.stop(appContext)
                        try {
                            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:$callerNumber")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            appContext.startActivity(dialIntent)
                        } catch (_: Exception) {}
                    }
                )

                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
                }

                @Suppress("DEPRECATION")
                val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    flags,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                    screenBrightness = 1.0f
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                wm.addView(root, params)
                overlayView = root
                isShowing = true
            } catch (e: Exception) {
                android.util.Log.e("SosAlarmOverlay", "Failed to show overlay window: ${e.message}")
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
            dismissInternal()
        }
    }

    private fun dismissInternal() {
        try {
            pulseAnimator?.cancel()
            pulseAnimator = null
            val view = overlayView
            if (view != null) {
                windowManager?.removeViewImmediate(view)
            }
        } catch (_: Exception) {
        } finally {
            overlayView = null
            isShowing = false
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildOverlayView(
        context: Context,
        callerName: String,
        callerNumber: String,
        missedCount: Int,
        pinRequired: Boolean,
        correctPin: String,
        onStopAlarm: () -> Unit,
        onCallBack: () -> Unit
    ): View {
        val rootFrame = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
        }

        // 1. Animated Pulsing Red Background Canvas View
        val pulseBgView = object : View(context) {
            var animProgress: Float = 0f
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val ringPaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            private val ringPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                bgPaint.shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    intArrayOf(
                        Color.parseColor("#450A14"),
                        Color.parseColor("#1E0409"),
                        Color.parseColor("#090103")
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                val cx = width / 2f
                val cy = height * 0.33f
                val baseRadius = min(width, height) * 0.26f

                val scale = 0.8f + (0.55f * animProgress)
                val alpha1 = ((1f - animProgress) * 140).toInt().coerceIn(0, 255)
                val alpha2 = ((1f - animProgress) * 95).toInt().coerceIn(0, 255)

                ringPaint1.color = Color.argb(alpha1, 239, 68, 68)
                ringPaint2.color = Color.argb(alpha2, 239, 68, 68)

                canvas.drawCircle(cx, cy, baseRadius * scale, ringPaint1)
                canvas.drawCircle(cx, cy, baseRadius * (scale * 1.25f), ringPaint2)
            }
        }

        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                pulseBgView.animProgress = animator.animatedValue as Float
                pulseBgView.invalidate()
            }
            start()
        }

        rootFrame.addView(
            pulseBgView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // 2. Main Foreground Content Container
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(context, 24), dp(context, 48), dp(context, 24), dp(context, 36))
        }

        // Top Emergency Pill Badge
        val badgeView = TextView(context).apply {
            text = "🚨 EMERGENCY MISSED CALL ALARM"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(context, 18), dp(context, 8), dp(context, 18), dp(context, 8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 50).toFloat()
                setColor(Color.argb(90, 239, 68, 68))
                setStroke(dp(context, 1), Color.parseColor("#EF4444"))
            }
        }
        contentLayout.addView(
            badgeView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Top Spacer
        contentLayout.addView(
            View(context),
            LinearLayout.LayoutParams(1, 0, 0.8f)
        )

        // Center Glowing Bell Circle
        val bellCircle = TextView(context).apply {
            text = "🔔"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#EF4444"))
                setStroke(dp(context, 3), Color.WHITE)
            }
        }
        contentLayout.addView(
            bellCircle,
            LinearLayout.LayoutParams(dp(context, 96), dp(context, 96)).apply {
                bottomMargin = dp(context, 20)
            }
        )

        // Caller Name
        val nameView = TextView(context).apply {
            text = callerName.ifBlank { "Emergency Contact" }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        contentLayout.addView(
            nameView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, 6)
            }
        )

        // Caller Number
        val numberView = TextView(context).apply {
            text = callerNumber
            setTextColor(Color.argb(220, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        }
        contentLayout.addView(
            numberView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, 14)
            }
        )

        // Missed Call Count
        val countView = TextView(context).apply {
            text = "Called $missedCount times repeatedly"
            setTextColor(Color.parseColor("#FFB4AB"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        contentLayout.addView(
            countView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, 4)
            }
        )

        // Last Call Time
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeView = TextView(context).apply {
            text = "Last missed call: ${timeFormat.format(Date())}"
            setTextColor(Color.argb(190, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
        }
        contentLayout.addView(
            timeView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Middle Spacer
        contentLayout.addView(
            View(context),
            LinearLayout.LayoutParams(1, 0, 1f)
        )

        // PIN Container (hidden unless pinRequired is triggered)
        val pinContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 18).toFloat()
                setColor(Color.parseColor("#2A0810"))
                setStroke(dp(context, 1), Color.parseColor("#EF4444"))
            }
        }

        val pinTitle = TextView(context).apply {
            text = "Enter 4-Digit PIN to Stop Alarm"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        pinContainer.addView(pinTitle)

        val pinInput = EditText(context).apply {
            hint = "••••"
            setHintTextColor(Color.argb(120, 255, 255, 255))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 12).toFloat()
                setColor(Color.argb(40, 255, 255, 255))
            }
        }
        pinContainer.addView(
            pinInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 48)
            ).apply {
                topMargin = dp(context, 10)
                bottomMargin = dp(context, 10)
            }
        )

        val pinConfirmBtn = TextView(context).apply {
            text = "UNLOCK & STOP ALARM"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 14).toFloat()
                setColor(Color.parseColor("#EF4444"))
            }
            setOnClickListener {
                if (pinInput.text.toString() == correctPin) {
                    onStopAlarm()
                } else {
                    pinTitle.text = "Incorrect PIN! Try Again"
                    pinTitle.setTextColor(Color.parseColor("#FCA5A5"))
                    pinInput.setText("")
                }
            }
        }
        pinContainer.addView(
            pinConfirmBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 46)
            )
        )

        contentLayout.addView(
            pinContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, 14)
            }
        )

        val triggerStopAction = {
            if (pinRequired) {
                pinContainer.visibility = View.VISIBLE
            } else {
                onStopAlarm()
            }
        }

        // 3. Call Back Button (Pure TextView so no AppCompat/Material theme is needed)
        val callBackBtn = TextView(context).apply {
            text = "📞  CALL BACK"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#2563EB"), Color.parseColor("#1D4ED8"))
            ).apply {
                cornerRadius = dp(context, 16).toFloat()
                setStroke(dp(context, 1), Color.argb(90, 255, 255, 255))
            }
            setOnClickListener {
                onCallBack()
            }
        }
        contentLayout.addView(
            callBackBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 56)
            ).apply {
                bottomMargin = dp(context, 14)
            }
        )

        // 4. Interactive Slide-to-Stop Track (Pure Custom Touch Drag View — zero SeekBar theme dependency)
        val sliderHeightPx = dp(context, 58)
        val thumbSizePx = dp(context, 48)
        val thumbMarginPx = dp(context, 5)

        val sliderFrame = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 30).toFloat()
                setColor(Color.argb(45, 255, 255, 255))
                setStroke(dp(context, 1), Color.argb(110, 239, 68, 68))
            }
        }

        val sliderLabel = TextView(context).apply {
            text = "SLIDE TO STOP ALARM  ➔"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        sliderFrame.addView(
            sliderLabel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val thumbView = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#EF4444"))
                setStroke(dp(context, 2), Color.WHITE)
            }
        }
        sliderFrame.addView(
            thumbView,
            FrameLayout.LayoutParams(thumbSizePx, thumbSizePx, Gravity.CENTER_VERTICAL or Gravity.START).apply {
                leftMargin = thumbMarginPx
            }
        )

        var downX = 0f
        var startTransX = 0f
        sliderFrame.setOnTouchListener { view, event ->
            val maxDrag = (view.width - thumbSizePx - thumbMarginPx * 2).coerceAtLeast(1).toFloat()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    startTransX = thumbView.translationX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawX - downX
                    val newX = (startTransX + delta).coerceIn(0f, maxDrag)
                    thumbView.translationX = newX
                    sliderLabel.alpha = (1f - (newX / maxDrag)).coerceIn(0.15f, 1f)
                    if (newX >= maxDrag * 0.8f) {
                        triggerStopAction()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (thumbView.translationX >= maxDrag * 0.8f) {
                        triggerStopAction()
                    } else {
                        thumbView.animate().translationX(0f).setDuration(180L).start()
                        sliderLabel.animate().alpha(1f).setDuration(180L).start()
                    }
                    true
                }
                else -> false
            }
        }

        contentLayout.addView(
            sliderFrame,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                sliderHeightPx
            ).apply {
                bottomMargin = dp(context, 14)
            }
        )

        // 5. Instant 1-Tap STOP ALARM Button
        val stopAlarmBtn = TextView(context).apply {
            text = "✕  STOP ALARM"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 16).toFloat()
                setColor(Color.parseColor("#DC2626"))
                setStroke(dp(context, 1), Color.argb(160, 255, 255, 255))
            }
            setOnClickListener {
                triggerStopAction()
            }
        }
        contentLayout.addView(
            stopAlarmBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 54)
            )
        )

        rootFrame.addView(
            contentLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        return rootFrame
    }
}
