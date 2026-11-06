package com.ayaraya.voicegunz

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private data class AppItem(val label: String, val pkg: String)

    private lateinit var root: FrameLayout
    private lateinit var bgIcon: ImageView
    private lateinit var gameIcon: ImageView
    private lateinit var plusIcon: ImageView
    private lateinit var gameName: TextView
    private lateinit var startBtn: Button
    private lateinit var a11yBtn: TextView

    private val reqOverlay = 21
    private val reqPerm = 22

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        bgIcon = findViewById(R.id.bgIcon)
        gameIcon = findViewById(R.id.gameIcon)
        plusIcon = findViewById(R.id.plusIcon)
        gameName = findViewById(R.id.gameName)
        startBtn = findViewById(R.id.startBtn)
        a11yBtn = findViewById(R.id.a11yBtn)

        findViewById<TextView>(R.id.logo).applyLogoGradient()
        findViewById<View>(R.id.pickBtn).setOnClickListener { showPicker() }
        startBtn.setOnClickListener { tryStart() }
        a11yBtn.setOnClickListener {
            Toast.makeText(
                this,
                "Cari \"Voice Gunz\" lalu aktifkan. Jika diblokir: Info Aplikasi > titik tiga > Izinkan setelan terbatas",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        refreshSelection()
    }

    override fun onResume() {
        super.onResume()
        val on = isA11yOn()
        a11yBtn.text = if (on) "Mode Kompatibel: AKTIF ✓" else "Aktifkan Mode Kompatibel (disarankan)"
        a11yBtn.setTextColor(if (on) 0xFF00E676.toInt() else 0xFFB39DFF.toInt())
    }

    private fun isA11yOn(): Boolean {
        val s = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return s.contains("$packageName/")
    }

    // ---------------------------------------------------------------- pilih game

    private fun showPicker() {
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val list = pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { if (!isFinishing) showPickerDialog(list) }
        }.start()
    }

    private fun showPickerDialog(items: List<AppItem>) {
        if (items.isEmpty()) {
            Toast.makeText(this, "Tidak ada aplikasi ditemukan", Toast.LENGTH_SHORT).show()
            return
        }
        val pm = packageManager
        val iconCache = HashMap<String, Drawable?>()

        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.item_app, parent, false)
                val item = items[position]
                v.findViewById<TextView>(R.id.appName).text = item.label
                val icon = iconCache.getOrPut(item.pkg) {
                    try { pm.getApplicationIcon(item.pkg) } catch (_: Exception) { null }
                }
                v.findViewById<ImageView>(R.id.appIcon).setImageDrawable(icon)
                return v
            }
        }

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Pilih game")
            .setAdapter(adapter) { _, which ->
                Prefs.setPkg(this, items[which].pkg)
                refreshSelection()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun refreshSelection() {
        val pkg = Prefs.getPkg(this)
        val pm = packageManager

        var label: String? = null
        var icon: Drawable? = null
        var color = 0
        if (pkg != null) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                label = pm.getApplicationLabel(info).toString()
                icon = pm.getApplicationIcon(pkg)
                // ikon terpisah untuk dihitung warnanya (agar bounds ikon tampilan tidak berubah)
                color = averageColor(pm.getApplicationIcon(pkg))
            } catch (_: PackageManager.NameNotFoundException) {
                label = null
                icon = null
            }
        }

        if (label == null || icon == null) {
            gameIcon.visibility = View.GONE
            plusIcon.visibility = View.VISIBLE
            gameName.text = "Ketuk + untuk memilih game"
            bgIcon.setImageDrawable(null)
            root.setBackgroundResource(R.drawable.bg_splash)
            startBtn.isEnabled = false
            startBtn.alpha = 0.4f
        } else {
            gameIcon.setImageDrawable(icon)
            gameIcon.visibility = View.VISIBLE
            plusIcon.visibility = View.GONE
            gameName.text = label
            bgIcon.setImageDrawable(pm.getApplicationIcon(pkg!!))
            root.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(darken(color, 0.6f), 0xFF0B0B14.toInt())
            )
            startBtn.isEnabled = true
            startBtn.alpha = 1f
        }
    }

    // ---------------------------------------------------------------- mulai

    private fun tryStart() {
        val pkg = Prefs.getPkg(this) ?: return

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Aktifkan \"Tampil di atas aplikasi lain\" untuk Voice Gunz",
                Toast.LENGTH_LONG
            ).show()
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                reqOverlay
            )
            return
        }

        val need = ArrayList<String>()
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) need.add(android.Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) need.add(android.Manifest.permission.POST_NOTIFICATIONS)

        if (need.isNotEmpty()) {
            requestPermissions(need.toTypedArray(), reqPerm)
            return
        }
        launchAll(pkg)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqOverlay) {
            if (Settings.canDrawOverlays(this)) tryStart()
            else Toast.makeText(this, "Izin overlay belum diberikan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != reqPerm) return
        val audioOk = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!audioOk) {
            Toast.makeText(this, "Izin mikrofon diperlukan untuk ubah suara", Toast.LENGTH_LONG).show()
            return
        }
        val pkg = Prefs.getPkg(this) ?: return
        launchAll(pkg)
    }

    private fun launchAll(pkg: String) {
        startForegroundService(Intent(this, OverlayService::class.java))

        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) {
            Toast.makeText(this, "Game tidak bisa dibuka", Toast.LENGTH_SHORT).show()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
    }
}
