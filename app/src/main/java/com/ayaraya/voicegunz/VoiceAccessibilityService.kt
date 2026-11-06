package com.ayaraya.voicegunz

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Service kosong untuk "Mode Kompatibel".
 * Di Android 10+ aplikasi dengan service aksesibilitas aktif lebih kecil kemungkinannya
 * dibungkam sistem saat aplikasi lain (WA/game) sedang memakai mikrofon.
 * Service ini TIDAK membaca isi layar dan tidak melakukan apa pun pada event.
 */
class VoiceAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // sengaja kosong
    }

    override fun onInterrupt() {
        // sengaja kosong
    }
}
