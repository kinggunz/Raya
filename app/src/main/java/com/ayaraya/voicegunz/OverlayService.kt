package com.ayaraya.voicegunz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.media.MediaPlayer
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
import java.io.File
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.ayaraya.voicegunz.STOP"
        private const val CHANNEL_ID = "voicegunz"
        private const val NOTIF_ID = 1
        private const val LONG_PRESS_MS = 450L
    }

    private lateinit var wm: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubble: TextView? = null
    private var menu: View? = null
    private var resultView: View? = null
    private var player: MediaPlayer? = null
    private var destroyed = false

    /** Suara yang sedang aktif (sudah dikonfirmasi). */
    private var active: VoicePreset? = null

    /** Suara terakhir yang dipilih, dipakai untuk tes lewat tekan lama. */
    private var lastPreset: VoicePreset? = null

    /** True saat tombol tes sedang ditahan. */
    private var testing = false

    /** True saat sedang merekam. */
    private var recording = false
    private var recSec = 0

    private val engine = VoiceEngine(
        onError = { msg ->
            mainHandler.post {
                active = null
                testing = false
                refreshBubble()
                toast(msg)
            }
        },
        onWarning = { msg -> mainHandler.post { toast(msg) } }
    )

    private val recorder = VoiceRecorder { file, err, secs ->
        mainHandler.post { onRecordDone(file, err, secs) }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!recording) return
            recSec++
            setBubbleLabel("${recSec}s", 15f)
            mainHandler.postDelayed(this, 1000)
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
        destroyed = true
        mainHandler.removeCallbacks(ticker)
        engine.stop()
        recorder.stop()
        hideMenu()
        hideResult()
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
            .setContentText("Ketuk logo untuk menu, tahan logo untuk tes suara")
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
        tv.setOnTouchListener(
            DragTouch(
                lp,
                onClick = { if (recording) stopRecording() else toggleMenu() },
                onLongPress = {
                    val p = active ?: lastPreset
                    if (p == null) toast("Pilih suara dulu lewat menu")
                    else startTest(p)
                },
                onLongRelease = { stopTest() }
            )
        )
        wm.addView(tv, lp)
        bubble = tv
    }

    private fun setBubbleLabel(text: String, size: Float) {
        bubble?.let {
            it.text = text
            it.textSize = size
        }
    }

    private fun refreshBubble() {
        val res = when {
            recording -> R.drawable.bg_bubble_rec
            active != null || testing -> R.drawable.bg_bubble_active
            else -> R.drawable.bg_bubble
        }
        bubble?.setBackgroundResource(res)
    }

    private inner class DragTouch(
        private val lp: WindowManager.LayoutParams,
        private val onClick: () -> Unit,
        private val onLongPress: () -> Unit,
        private val onLongRelease: () -> Unit
    ) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false
        private var longActive = false

        private val longRunnable = Runnable {
            if (!moved) {
                longActive = true
                onLongPress()
            }
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = e.rawX; touchY = e.rawY
                    moved = false
                    longActive = false
                    mainHandler.postDelayed(longRunnable, LONG_PRESS_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (!longActive && (abs(dx) > dp(6) || abs(dy) > dp(6))) {
                        moved = true
                        mainHandler.removeCallbacks(longRunnable)
                    }
                    if (moved) {
                        lp.x = (startX + dx).toInt()
                        lp.y = (startY + dy).toInt()
                        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longRunnable)
                    if (longActive) {
                        longActive = false
                        onLongRelease()
                    } else if (!moved && e.action == MotionEvent.ACTION_UP) {
                        onClick()
                    }
                }
            }
            return true
        }
    }

    // ---------------------------------------------------------------- menu ubah suara

    private fun toggleMenu() {
        if (menu != null) hideMenu() else showMenu()
    }

    private fun selectedPreset(group: RadioGroup): VoicePreset? = when (group.checkedRadioButtonId) {
        R.id.optChild -> VoicePreset.CHILD
        R.id.optMom -> VoicePreset.MOM
        R.id.optDad -> VoicePreset.DAD
        else -> null
    }

    private fun panelParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.CENTER }

    private fun showMenu() {
        hideResult()
        val v = LayoutInflater.from(this).inflate(R.layout.overlay_menu, null)
        val group = v.findViewById<RadioGroup>(R.id.group)

        when (active ?: lastPreset) {
            VoicePreset.CHILD -> group.check(R.id.optChild)
            VoicePreset.MOM -> group.check(R.id.optMom)
            VoicePreset.DAD -> group.check(R.id.optDad)
            null -> {}
        }

        // Tahan untuk tes: selama ditahan, suara kita diubah dan terdengar langsung.
        v.findViewById<View>(R.id.btnTest).setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    val p = selectedPreset(group) ?: active ?: lastPreset
                    if (p == null) toast("Pilih salah satu suara dulu")
                    else {
                        lastPreset = p
                        startTest(p)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopTest()
            }
            true
        }

        // Rekam suara yang sudah diubah, lalu kirim sebagai file ke WA / aplikasi lain.
        v.findViewById<View>(R.id.btnRecord).setOnClickListener {
            val p = selectedPreset(group) ?: active ?: lastPreset
            if (p == null) toast("Pilih salah satu suara dulu")
            else startRecording(p)
        }

        v.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            val preset = selectedPreset(group)
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
            testing = false
            refreshBubble()
            hideMenu()
            toast("Suara kembali normal")
        }
        v.findViewById<View>(R.id.btnClose).setOnClickListener { hideMenu() }

        wm.addView(v, panelParams())
        menu = v
    }

    private fun hideMenu() {
        menu?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        menu = null
    }

    private fun applyPreset(preset: VoicePreset) {
        if (recording) {
            toast("Sedang merekam, hentikan dulu")
            return
        }
        lastPreset = preset
        engine.preset = preset
        engine.start()
        active = preset
        refreshBubble()
        toast("Suara aktif: ${preset.label}")
    }

    // ---------------------------------------------------------------- tes suara

    private fun startTest(p: VoicePreset) {
        if (testing || recording) return
        testing = true
        engine.preset = p
        engine.start()
        refreshBubble()
        toast("Tes suara: bicara sekarang. Pakai headset supaya tidak berdengung")
    }

    private fun stopTest() {
        if (!testing) return
        testing = false
        val a = active
        if (a != null) engine.preset = a else engine.stop()
        refreshBubble()
    }

    // ---------------------------------------------------------------- rekam & kirim

    private fun startRecording(p: VoicePreset) {
        if (recording) return
        // mic hanya bisa dipakai satu proses: matikan mode live dulu
        engine.stop()
        active = null
        testing = false
        lastPreset = p
        hideMenu()
        hideResult()

        recording = true
        recSec = 0
        refreshBubble()
        setBubbleLabel("0s", 15f)
        mainHandler.postDelayed(ticker, 1000)
        recorder.start(p, ShareProvider.dir(this))
        toast("Merekam suara ${p.label}. Ketuk logo lagi untuk berhenti")
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        mainHandler.removeCallbacks(ticker)
        setBubbleLabel("A", 24f)
        refreshBubble()
        toast("Memproses rekaman...")
        recorder.stop()
    }

    private fun onRecordDone(file: File?, err: String?, secs: Int) {
        if (destroyed) return
        if (recording) {
            // berhenti otomatis karena batas durasi
            recording = false
            mainHandler.removeCallbacks(ticker)
            setBubbleLabel("A", 24f)
        }
        refreshBubble()
        if (file == null) {
            toast(err ?: "Gagal merekam")
            return
        }
        showResult(file, secs)
    }

    private fun showResult(file: File, secs: Int) {
        hideMenu()
        hideResult()
        val v = LayoutInflater.from(this).inflate(R.layout.overlay_result, null)
        v.findViewById<TextView>(R.id.resInfo).text = "Durasi: $secs dtk"

        val btnPlay = v.findViewById<TextView>(R.id.btnPlay)
        btnPlay.setOnClickListener { togglePlay(file, btnPlay) }

        v.findViewById<View>(R.id.btnShare).setOnClickListener {
            hideResult()
            shareFile(file)
        }
        v.findViewById<View>(R.id.btnDelete).setOnClickListener {
            hideResult()
            try { file.delete() } catch (_: Exception) {}
            toast("Rekaman dibuang")
        }
        v.findViewById<View>(R.id.btnCloseRes).setOnClickListener { hideResult() }

        wm.addView(v, panelParams())
        resultView = v
    }

    private fun hideResult() {
        stopPlayer()
        resultView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        resultView = null
    }

    private fun togglePlay(file: File, btn: TextView) {
        if (player != null) {
            stopPlayer()
            btn.text = "▶ Putar"
            return
        }
        try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                stopPlayer()
                btn.text = "▶ Putar"
            }
            mp.prepare()
            mp.start()
            player = mp
            btn.text = "■ Stop"
        } catch (_: Exception) {
            stopPlayer()
            btn.text = "▶ Putar"
            toast("Gagal memutar rekaman")
        }
    }

    private fun stopPlayer() {
        player?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        player = null
    }

    private fun shareFile(file: File) {
        val uri = ShareProvider.uriFor(file.name)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Voice Gunz", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Kirim suara lewat").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = send.clipData
        }
        try {
            startActivity(chooser)
        } catch (_: Exception) {
            toast("Menu kirim tidak bisa dibuka. Cek izin pop-up latar belakang untuk Voice Gunz")
        }
    }

    // ---------------------------------------------------------------- util

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
