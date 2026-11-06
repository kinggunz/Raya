# Voice Gunz (by Ayaraya)

Aplikasi Android pengubah suara dengan logo overlay melayang.
Mendukung Android 8 – 15 (minSdk 26, compileSdk 35).

## Alur aplikasi
1. Splash dengan logo tulisan **Ayaraya**
2. Ketuk tombol **+** di tengah → pilih satu game/aplikasi (tampil logo + background sesuai ikon aplikasi)
3. Ketuk **Mulai** → game terbuka, logo overlay muncul
4. Ketuk logo overlay → **Ubah Suara**: Anak Kecil / Ibu-ibu / Bapak-bapak → **Konfirmasi**
5. Logo overlay berbingkai hijau saat pengubah suara aktif. Menu juga punya tombol "Matikan pengubah suara".

## Build lewat Termux + GitHub Actions
1. Salin `VoiceGunz.zip` ke folder Download, lalu di Termux:
   ```bash
   termux-setup-storage
   cp ~/storage/downloads/VoiceGunz.zip ~
   unzip VoiceGunz.zip && cd VoiceGunz
   bash termux-push.sh
   ```
2. Masukkan username GitHub, nama repo, dan token GitHub
   (buat di GitHub → Settings → Developer settings → Personal access tokens, centang `repo` dan `workflow`).
3. Script akan push, menunggu build selesai, lalu mengunduh APK ke `Download/VoiceGunz/app-debug.apk`.

Atau manual: push folder ini ke GitHub → tab **Actions** → **Build APK** → unduh artifact `VoiceGunz-debug-apk`.

## Catatan penting soal suara
Android tanpa root **tidak mengizinkan** aplikasi mengganti mikrofon aplikasi lain secara langsung.
Voice Gunz mengambil suara dari mikrofon, mengubah nada secara real-time, lalu memutarnya lewat speaker
agar terdengar oleh mikrofon game/chat. Hasilnya bergantung pada perangkat dan versi Android
(Android 10+ memprioritaskan mikrofon aplikasi yang sedang di layar, jadi di sebagian HP suara bisa tidak masuk).
Untuk penggantian mikrofon yang benar-benar langsung, dibutuhkan root / modul virtual mic.

Mengatur nada: ubah nilai `ratio` di `VoicePreset.kt` (di atas 1 = lebih tinggi, di bawah 1 = lebih rendah).
