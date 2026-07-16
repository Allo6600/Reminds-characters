package com.reminds.characters.overlay

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.reminds.characters.MainActivity
import com.reminds.characters.R
import com.reminds.characters.data.TaskDao
import com.reminds.characters.data.TaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.random.Random

/**
 * ホーム画面（他のアプリの上）にキャラ「リマ」を常駐させるオーバーレイサービス。
 * キャラは画面をふらふら歩き回り、頭上の吹き出しで次のタスクを教えてくれる。
 * ドラッグで移動、タップでアプリを開く。
 */
class MascotOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var bubble: TextView
    private lateinit var mascot: ImageView
    private lateinit var params: WindowManager.LayoutParams

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var walker: ValueAnimator? = null
    private var bob: ObjectAnimator? = null
    private var dragging = false
    private var screenOn = true

    private val timeFormat = DateTimeFormatter.ofPattern("H:mm")

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            screenOn = intent?.action != Intent.ACTION_SCREEN_OFF
            if (!screenOn) walker?.cancel()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        startAsForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        buildView()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        observeBubble()
        startWandering()
        startBobbing()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            OverlayPrefs.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        walker?.cancel()
        bob?.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        if (::root.isInitialized) runCatching { windowManager.removeView(root) }
        super.onDestroy()
    }

    // ---- 表示 ----

    @SuppressLint("ClickableViewAccessibility")
    private fun buildView() {
        bubble = TextView(this).apply {
            setBackgroundResource(R.drawable.bg_bubble)
            setTextColor(0xFF5D4037.toInt())
            textSize = 13f
            maxWidth = dp(220)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            text = getString(R.string.overlay_idle)
        }
        mascot = ImageView(this).apply {
            setImageResource(R.drawable.mascot)
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96)).apply { topMargin = dp(2) }
            contentDescription = getString(R.string.character_name)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(bubble)
            addView(mascot)
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(40)
            y = resources.displayMetrics.heightPixels - dp(340)
        }
        windowManager.addView(root, params)

        // ドラッグで移動、動かさずに離したらタップ＝アプリを開く
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startPX = 0
        var startPY = 0
        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    downX = e.rawX; downY = e.rawY
                    startPX = params.x; startPY = params.y
                    walker?.cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) dragging = true
                    if (dragging) {
                        params.x = (startPX + dx).toInt().coerceAtLeast(0)
                        params.y = (startPY + dy).toInt().coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(root, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) openApp()
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // ---- 動き ----

    /** 数秒おきに画面内のランダムな場所へ、てくてく歩く */
    private fun startWandering() {
        scope.launch {
            while (isActive) {
                delay(Random.nextLong(3_000, 8_000))
                if (!dragging && screenOn) {
                    val maxX = (resources.displayMetrics.widthPixels - rootWidth()).coerceAtLeast(1)
                    strollTo(Random.nextInt(0, maxX))
                }
            }
        }
    }

    private fun strollTo(targetX: Int) {
        val startX = params.x
        val distance = abs(targetX - startX)
        if (distance < dp(16)) return
        walker?.cancel()
        walker = ValueAnimator.ofInt(startX, targetX).apply {
            duration = (distance * 6L).coerceIn(800L, 4_500L)
            interpolator = LinearInterpolator()
            addUpdateListener {
                params.x = it.animatedValue as Int
                runCatching { windowManager.updateViewLayout(root, params) }
            }
            start()
        }
    }

    /** その場でぷかぷか上下に揺れる（生きてる感） */
    private fun startBobbing() {
        bob = ObjectAnimator.ofFloat(mascot, "translationY", 0f, -dp(6).toFloat(), 0f).apply {
            duration = 1_400
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun rootWidth(): Int = if (root.width > 0) root.width else dp(160)

    // ---- 吹き出し ----

    /** タスクの変化を監視しつつ、20秒ごとにセリフを見直す（時間経過で催促に変わる） */
    private fun observeBubble() {
        scope.launch {
            val dao = TaskDatabase.get(this@MascotOverlayService).taskDao()
            dao.observeAll().collectLatest {
                while (isActive) {
                    bubble.text = bubbleText(dao)
                    delay(20_000)
                }
            }
        }
    }

    private suspend fun bubbleText(dao: TaskDao): CharSequence {
        val next = dao.nextPending() ?: return getString(R.string.overlay_idle)
        return if (next.remindAtMillis <= System.currentTimeMillis()) {
            next.bubbleMessage
        } else {
            val base = getString(R.string.widget_next, next.memo, next.remindAt.format(timeFormat))
            val deadline = next.deadline?.let {
                getString(R.string.widget_deadline_suffix, it.format(timeFormat))
            } ?: ""
            base + deadline
        }
    }

    // ---- 常駐通知 ----

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MascotOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(0, getString(R.string.overlay_stop), stopIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_STOP = "com.reminds.characters.overlay.STOP"
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 1001

        /** 設定ONかつ権限があればキャラを出す */
        fun startIfEnabled(context: Context) {
            if (OverlayPrefs.isEnabled(context) && Settings.canDrawOverlays(context)) {
                runCatching {
                    context.startForegroundService(Intent(context, MascotOverlayService::class.java))
                }
            }
        }
    }
}
