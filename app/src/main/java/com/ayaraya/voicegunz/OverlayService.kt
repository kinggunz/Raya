package com.ayaraya.voicegunz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.ayaraya.voicegunz.STOP"
        private const val CHANNEL_ID = "voicegunz"
        private const val NOTIF_ID = 1
    }

    private lateinit var wm: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubble: TextView? = null
    private var menu: View? = null
    private var active: VoicePreset? = null

    private val engine = VoiceEngine { msg ->
        mainHandler.post {
            active = null
            refreshBubble()
            toast(msg)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        engine.stop()
        hideMenu()
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- notifikasi

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Voice Gunz", NotificationManager.IMPORTANCE_LOW)
        )

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Voice Gunz aktif")
            .setContentText("Ketuk logo melayang untuk ubah suara")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat), "Berhenti", stop
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    // ---------------------------------------------------------------- logo melayang

    private fun showBubble() {
        val size = dp(58)
        val tv = TextView(this).apply {
            text = "A"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundResource(R.drawable.bg_bubble)
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(200)
        }
        tv.setOnTouchListener(DragTouch(lp) { toggleMenu() })
        wm.addView(tv, lp)
        bubble = tv
    }

    private fun refreshBubble() {
        bubble?.setBackgroundResource(
            if (active != null) R.drawable.bg_bubble_active else R.drawable.bg_bubble
        )
    }

    private inner class DragTouch(
        private val lp: WindowManager.LayoutParams,
        private val onClick: () -> Unit
    ) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = e.rawX; touchY = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        lp.x = (startX + dx).toInt()
                        lp.y = (startY + dy).toInt()
                        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) onClick()
            }
            return true
        }
    }

    // ---------------------------------------------------------------- menu ubah suara

    private fun toggleMenu() {
        if (menu != null) hideMenu() else showMenu()
    }

    private fun showMenu() {
        val v = LayoutInflater.from(this).inflate(R.layout.overlay_menu, null)
        val group = v.findViewById<RadioGroup>(R.id.group)

        when (active) {
            VoicePreset.CHILD -> group.check(R.id.optChild)
            VoicePreset.MOM -> group.check(R.id.optMom)
            VoicePreset.DAD -> group.check(R.id.optDad)
            null -> {}
        }

        v.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            val preset = when (group.checkedRadioButtonId) {
                R.id.optChild -> VoicePreset.CHILD
                R.id.optMom -> VoicePreset.MOM
                R.id.optDad -> VoicePreset.DAD
                else -> null
            }
            if (preset == null) {
                toast("Pilih salah satu suara dulu")
            } else {
                applyPreset(preset)
                hideMenu()
            }
        }
        v.findViewById<View>(R.id.btnOff).setOnClickListener {
            engine.stop()
            active = null
            refreshBubble()
            hideMenu()
            toast("Suara kembali normal")
        }
        v.findViewById<View>(R.id.btnClose).setOnClickListener { hideMenu() }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        wm.addView(v, lp)
        menu = v
    }

    private fun hideMenu() {
        menu?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        menu = null
    }

    private fun applyPreset(preset: VoicePreset) {
        engine.preset = preset
        engine.start()
        active = preset
        refreshBubble()
        toast("Suara aktif: ${preset.label}")
    }

    // ---------------------------------------------------------------- util

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
