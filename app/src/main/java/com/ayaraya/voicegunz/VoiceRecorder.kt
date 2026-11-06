package com.ayaraya.voicegunz

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import java.io.File
import kotlin.math.max

/**
 * Merekam suara dari mikrofon, mengubah nada, lalu menyimpannya sebagai file .m4a.
 * Mikrofon hanya dipakai selama merekam, jadi tidak bentrok dengan WA saat mengirim.
 */
class VoiceRecorder(
    private val onDone: (file: File?, error: String?, seconds: Int) -> Unit
) {

    companion object {
        const val RATE = 44100
        const val MAX_SECONDS = 120
    }

    @Volatile private var stopFlag = false
    @Volatile var isRunning = false
        private set

    @Synchronized
    fun start(preset: VoicePreset, dir: File) {
        if (isRunning) return
        isRunning = true
        stopFlag = false
        Thread({ work(preset, dir) }, "VoiceRecorder").start()
    }

    fun stop() {
        stopFlag = true
    }

    private fun work(preset: VoicePreset, dir: File) {
        var file: File? = null
        var err: String? = null
        var secs = 0
        try {
            val pcm = record(preset)
            if (pcm.size < RATE / 4) {
                throw IllegalStateException("Rekaman terlalu singkat")
            }
            if (pcm.all { it.toInt() == 0 }) {
                throw IllegalStateException(
                    "Rekaman kosong: mic dibungkam sistem atau sedang dipakai aplikasi lain"
                )
            }
            cleanup(dir)
            val f = File(dir, "voice_${System.currentTimeMillis()}.m4a")
            AacEncoder.encode(pcm, RATE, f)
            file = f
            secs = pcm.size / RATE
        } catch (e: Exception) {
            err = e.message ?: "Gagal merekam"
        }
        isRunning = false
        onDone(file, err, secs)
    }

    @SuppressLint("MissingPermission")
    private fun record(preset: VoicePreset): ShortArray {
        Thread.sleep(150) // beri waktu mic dilepas oleh proses sebelumnya
        val frame = 1024
        val minIn = AudioRecord.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minIn, frame * 4)
        )
        var ns: NoiseSuppressor? = null
        try {
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException(
                    "Mikrofon tidak bisa dibuka. Pastikan izin mikrofon aktif dan tidak dipakai aplikasi lain."
                )
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(rec.audioSessionId)
                ns?.enabled = true
            }

            val maxSamples = RATE * MAX_SECONDS
            val out = ShortArray(maxSamples)
            var count = 0
            val inBuf = ShortArray(frame)
            val outBuf = ShortArray(frame)
            val shifter = PitchShifter()
            shifter.ratio = preset.ratio
            shifter.gain = 1.3

            rec.startRecording()
            while (!stopFlag && count + frame <= maxSamples) {
                val n = rec.read(inBuf, 0, frame)
                if (n < 0) throw IllegalStateException("Mikrofon terputus.")
                if (n == 0) continue
                shifter.process(inBuf, outBuf, n)
                System.arraycopy(outBuf, 0, out, count, n)
                count += n
            }
            return out.copyOf(count)
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            try { rec.release() } catch (_: Exception) {}
            try { ns?.release() } catch (_: Exception) {}
        }
    }

    /** Simpan maksimal 5 rekaman terakhir. */
    private fun cleanup(dir: File) {
        val files = dir.listFiles { f -> f.name.endsWith(".m4a") } ?: return
        files.sortedByDescending { it.lastModified() }.drop(4).forEach {
            try { it.delete() } catch (_: Exception) {}
        }
    }
}
