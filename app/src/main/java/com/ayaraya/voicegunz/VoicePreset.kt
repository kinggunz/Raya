package com.ayaraya.voicegunz

/** Pilihan suara. [ratio] > 1 = nada naik, < 1 = nada turun. */
enum class VoicePreset(val label: String, val ratio: Double) {
    CHILD("Suara Anak Kecil", 1.65),
    MOM("Suara Ibu-ibu", 1.30),
    DAD("Suara Bapak-bapak", 0.78)
}
