package com.carpersona.app.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.carpersona.app.persona.Register
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 기념일 알림음. 소리 파일 없이 코드로 만듭니다.
 * 성격(말투)마다 다른 소리:
 *  - 새내기 : 높고 빠르게 올라가는 "띠리링!"
 *  - 친구   : 부드러운 "띵-동"
 *  - 어르신 : 낮고 느린 종소리 "댕-, 댕-"
 */
object Chime {
    private const val RATE = 22_050

    /** (주파수 Hz, 길이 ms) 목록 */
    private fun melody(r: Register): List<Pair<Double, Int>> = when (r) {
        Register.ROOKIE -> listOf(1046.5 to 110, 1318.5 to 110, 1568.0 to 110, 2093.0 to 380)   // 도-미-솔-도(높은)
        Register.BUDDY -> listOf(659.3 to 380, 523.3 to 600)                                        // 미-도
        Register.ELDER -> listOf(392.0 to 750, 523.3 to 1100)                                       // 솔-도(낮고 길게)
    }

    /** 알림음 전체 길이 (이 시간 뒤에 말을 시작) */
    fun durationMs(r: Register): Long = melody(r).sumOf { it.second }.toLong() + 150

    fun play(r: Register) {
        Thread {
            runCatching {
                val pcm = render(melody(r))
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / RATE + 100)
                track.release()
            }
        }.start()
    }

    /** 종소리처럼: 사인파 + 배음, 점점 작아짐 */
    private fun render(notes: List<Pair<Double, Int>>): ShortArray {
        val out = ArrayList<Short>()
        for ((f, ms) in notes) {
            val n = RATE * ms / 1000
            for (i in 0 until n) {
                val t = i.toDouble() / RATE
                val env = exp(-3.0 * t / (ms / 1000.0)) * minOf(1.0, i / 200.0)
                val v = (sin(2 * PI * f * t) + 0.3 * sin(2 * PI * f * 2 * t)) * env * 0.35
                out += (v * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out.toShortArray()
    }
}
