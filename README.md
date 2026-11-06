# Voice Gunz (by Ayaraya) v1.1

Aplikasi Android pengubah suara dengan logo overlay melayang.
Mendukung Android 8 – 15 (minSdk 26, compileSdk 35).

## Alur aplikasi
1. Splash dengan logo tulisan **Ayaraya**
2. Ketuk tombol **+** di tengah → pilih satu game/aplikasi (tampil logo + background sesuai ikon aplikasi)
3. Ketuk **Mulai** → game/aplikasi terbuka, logo overlay muncul
4. Ketuk logo → menu **Ubah Suara**: Anak Kecil / Ibu-ibu / Bapak-bapak

## Tiga cara memakai suara
| Cara | Fungsi |
|------|--------|
| **Konfirmasi** | Mode live: suara diubah real-time lalu diputar lewat speaker/headset. Pada sebagian HP ini membuat WA menolak merekam (lihat catatan). |
| **Tes (tahan)** | Tahan tombol (atau tahan logo) → suara kamu berubah dan terdengar langsung. Lepas untuk berhenti. Pakai headset. |
| **● Rekam & Kirim** | **Disarankan untuk WA.** Rekam suara yang sudah diubah → mic dilepas → putar/kirim sebagai file ke WA atau aplikasi apa pun. |

### Rekam & Kirim
1. Buka menu, pilih suara, ketuk **● Rekam & Kirim** (menu tertutup, logo jadi merah dan menampilkan detik).
2. Bicara. Ketuk logo lagi untuk berhenti (otomatis berhenti di 2 menit).
3. Muncul panel **Rekaman siap**: **▶ Putar** untuk cek, **Kirim ke WA / aplikasi lain** untuk membagikan, atau **Buang**.
4. Pilih WhatsApp → pilih chat → kirim. File berformat .m4a.

Jika menu kirim tidak muncul di HP Oppo/Realme/Xiaomi dll, aktifkan izin
**"Tampilkan pop-up saat berjalan di latar belakang"** untuk Voice Gunz di Info Aplikasi → Izin.

## Mode Kompatibel (Aksesibilitas)
Opsional. Service aksesibilitas Voice Gunz **kosong dan tidak membaca layar**; fungsinya hanya membuat sistem
Android 10+ lebih mungkin membiarkan Voice Gunz menangkap mikrofon di samping aplikasi lain.
Di Android 13+: Info Aplikasi → titik tiga → **Izinkan setelan terbatas**, lalu aktifkan di Setelan → Aksesibilitas.

## Build lewat Termux + GitHub Actions
```bash
termux-setup-storage
cp ~/storage/downloads/VoiceGunz.zip ~
unzip VoiceGunz.zip && cd VoiceGunz
bash termux-push.sh
```
Masukkan username GitHub, nama repo, dan token (scope `repo` + `workflow`).
APK diunduh ke `Download/VoiceGunz/app-debug.apk`.
Atau manual: push ke GitHub → tab **Actions** → **Build APK** → unduh artifact `VoiceGunz-debug-apk`.

## Catatan penting
Android tanpa root **tidak mengizinkan** aplikasi mengganti mikrofon aplikasi lain, dan sebagian HP hanya
membolehkan satu aplikasi memakai mic dalam satu waktu. Karena itu mode live tidak dijamin bekerja di WA.
Pakai **Rekam & Kirim**, atau **Matikan pengubah suara** sebelum merekam langsung di WA.
Penggantian mic live yang bersih di semua aplikasi hanya bisa lewat root / modul virtual mic.

Atur nada: ubah `ratio` di `VoicePreset.kt` (di atas 1 = lebih tinggi, di bawah 1 = lebih rendah).
