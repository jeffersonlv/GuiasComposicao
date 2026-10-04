package com.jef.guiascomposicao

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.jef.guiascomposicao.STOP"
        private const val CHANNEL_ID = "overlay"
        private const val NOTIF_ID = 1
        @Volatile var isRunning = false
    }

    private lateinit var wm: WindowManager
    private var guidesView: GuidesView? = null
    private var panel: View? = null
    private lateinit var panelParams: WindowManager.LayoutParams
    private val prefs by lazy { getSharedPreferences("overlay", MODE_PRIVATE) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground() // precisa vir primeiro, senão o Android derruba o app
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addGuidesLayer()
        addControlPanel()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        listOfNotNull(guidesView, panel).forEach { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }

    // ---------- Camada 1: guias (não recebe toque) ----------

    private fun addGuidesLayer() {
        val view = GuidesView(this).apply { guide = savedGuide() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // IMPORTANTE: a partir do Android 12, um overlay só deixa o toque
            // passar para o app de baixo se a opacidade da janela for <= 0.8.
            alpha = 0.8f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        wm.addView(view, params)
        guidesView = view
    }

    // ---------- Camada 2: controles (arrastáveis) ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun addControlPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0xCC1C1C1E.toInt())
            }
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val handle = chip("⠿").apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f) }
        val selector = chip("${savedGuide().label}  ▾")
        val close = chip("✕")
        bar.addView(handle); bar.addView(selector); bar.addView(close)

        // "Dropdown": lista que abre dentro da própria janela.
        // (Spinner nativo não funciona bem em janela de overlay.)
        val options = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        GuideType.entries.forEach { type ->
            options.addView(chip(type.label).apply {
                setOnClickListener {
                    guidesView?.guide = type
                    prefs.edit().putString("guide", type.name).apply()
                    selector.text = "${type.label}  ▾"
                    options.visibility = View.GONE
                }
            })
        }

        selector.setOnClickListener {
            options.visibility = if (options.visibility == View.GONE) View.VISIBLE else View.GONE
        }
        close.setOnClickListener { stopSelf() }

        root.addView(bar)
        root.addView(options)

        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Fora do painel, os toques continuam indo para o app de baixo.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", dp(16))
            y = prefs.getInt("y", dp(120))
        }

        // Arrastar pela alça ⠿
        handle.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0; var startY = 0
            var touchX = 0f; var touchY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = panelParams.x; startY = panelParams.y
                        touchX = e.rawX; touchY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        panelParams.x = startX + (e.rawX - touchX).roundToInt()
                        panelParams.y = startY + (e.rawY - touchY).roundToInt()
                        wm.updateViewLayout(root, panelParams)
                    }
                    MotionEvent.ACTION_UP -> {
                        prefs.edit().putInt("x", panelParams.x).putInt("y", panelParams.y).apply()
                        v.performClick()
                    }
                }
                return true
            }
        })

        wm.addView(root, panelParams)
        panel = root
    }

    private fun chip(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(0xFFFFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        isClickable = true
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun savedGuide(): GuideType =
        runCatching { GuideType.valueOf(prefs.getString("guide", null)!!) }
            .getOrDefault(GuideType.THIRDS)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    // ---------- Notificação obrigatória do Foreground Service ----------

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Overlay de guias", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Guias de composição ativos")
            .setContentText("Toque em Fechar para remover o overlay")
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Fechar", stopIntent
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }
}
