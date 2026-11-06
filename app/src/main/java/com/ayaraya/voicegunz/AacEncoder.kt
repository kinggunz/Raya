package com.ayaraya.voicegunz

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/** Mengubah PCM 16-bit mono menjadi file .m4a (AAC) yang bisa dikirim ke WA dan aplikasi lain. */
object AacEncoder {

    fun encode(pcm: ShortArray, sampleRate: Int, out: File) {
        val data = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            val s = pcm[i].toInt()
            data[2 * i] = (s and 0xFF).toByte()
            data[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64000)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m

            var track = -1
            val info = MediaCodec.BufferInfo()
            var offset = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000L)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        buf.clear()
                        val remaining = data.size - offset
                        var n = minOf(remaining, buf.capacity())
                        n -= n % 2
                        if (n <= 0) {
                            codec.queueInputBuffer(idx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.put(data, offset, n)
                            val pts = (offset / 2L) * 1_000_000L / sampleRate
                            codec.queueInputBuffer(idx, 0, n, pts, 0)
                            offset += n
                        }
                    }
                }

                val oidx = codec.dequeueOutputBuffer(info, 10_000L)
                if (oidx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = m.addTrack(codec.outputFormat)
                    m.start()
                    muxerStarted = true
                } else if (oidx >= 0) {
                    val ob = codec.getOutputBuffer(oidx)!!
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        info.size = 0
                    }
                    if (info.size > 0 && muxerStarted) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        m.writeSampleData(track, ob, info)
                    }
                    codec.releaseOutputBuffer(oidx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
            if (muxerStarted) {
                try { muxer?.stop() } catch (_: Exception) {}
            }
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
