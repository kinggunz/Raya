package com.ayaraya.voicegunz

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlin.math.max

/**
 * Mengambil suara dari mikrofon, mengubah nada secara real-time,
 * lalu memutarnya lewat speaker/headset.
 */
class VoiceEngine(
    private val onError: (String) -> Unit,
    private val onWarning: (String) -> Unit = {}
) {

    @Volatile private var running = false
    @Volatile private var gen = 0
    @Volatile var preset: VoicePreset = VoicePreset.CHILD

    @Synchronized
    fun start() {
        if (running) return
        running = true
        gen++
        val g = gen
        Thread({ loop(g) }, "VoiceEngine").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Synchronized
    fun stop() {
        running = false
        gen++
    }

    @SuppressLint("MissingPermission")
    private fun loop(g: Int) {
        val rate = 44100
        val frame = 1024
        var rec: AudioRecord? = null
        var track: AudioTrack? = null
        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null

        try {
            val minIn = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minIn, frame * 4)
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                fail(g, "Mikrofon tidak bisa dibuka. Pastikan izin mikrofon aktif.")
                return
            }

            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(rec.audioSessionId)
                aec?.enabled = true
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(rec.audioSessionId)
                ns?.enabled = true
            }

            val minOut = AudioTrack.getMinBufferSize(
                rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minOut, frame * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            val inBuf = ShortArray(frame)
            val outBuf = ShortArray(frame)
            val shifter = PitchShifter()
            shifter.gain = 1.5

            rec.startRecording()
            track.play()

            var silentFrames = 0

            while (running && g == gen) {
                val n = rec.read(inBuf, 0, frame)
                if (n < 0) {
                    fail(g, "Mikrofon terputus.")
                    break
                }
                if (n == 0) continue

                // Deteksi mic dibungkam sistem (semua sampel persis nol)
                var allZero = true
                for (i in 0 until n) {
                    if (inBuf[i].toInt() != 0) { allZero = false; break }
                }
                if (allZero) {
                    silentFrames++
                    if (silentFrames == 60 || silentFrames == 1500) {
                        onWarning(
                            "Mic tidak menerima suara. Kemungkinan dibungkam sistem karena " +
                                "aplikasi lain memakai mic. Aktifkan Mode Kompatibel di layar utama."
                        )
                    }
                } else {
                    silentFrames = 0
                }

                shifter.ratio = preset.ratio
                shifter.process(inBuf, outBuf, n)
                track.write(outBuf, 0, n)
            }
        } catch (e: Exception) {
            fail(g, "Gagal memproses suara: ${e.message}")
        } finally {
            try { rec?.stop() } catch (_: Exception) {}
            try { rec?.release() } catch (_: Exception) {}
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
            try { aec?.release() } catch (_: Exception) {}
            try { ns?.release() } catch (_: Exception) {}
        }
    }

    private fun fail(g: Int, msg: String) {
        if (g == gen) {
            running = false
            onError(msg)
        }
    }
}
