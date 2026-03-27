package com.example.liveai.live2d

import android.content.Context
import com.example.liveai.FilterSettings
import com.example.liveai.audio.AudioDrivenMotion
import com.example.liveai.audio.AudioMotionConfig
import com.example.liveai.audio.AudioVolumeSource
import com.example.liveai.gyroscope.GyroscopeDrivenMotion
import com.example.liveai.gyroscope.GyroscopeTiltSource

/**
 * Encapsulates the common Live2D setup shared between OverlayService,
 * WallpaperSetupActivity, and Live2DWallpaperService.
 */
data class Live2DSession(
    val textureManager: LAppTextureManager,
    val manager: LAppLive2DManager,
    val audioSource: AudioVolumeSource,
    val audioMotion: AudioDrivenMotion,
    val tiltSource: GyroscopeTiltSource,
    val gyroscopeMotion: GyroscopeDrivenMotion
)

object Live2DSessionFactory {

    fun create(context: Context, testAudio: Boolean = false): Live2DSession {
        val prefs = context.getSharedPreferences(FilterSettings.PREFS_NAME, Context.MODE_PRIVATE)

        val audioSource = AudioVolumeSource(context)
        if (testAudio) {
            audioSource.testMode = true
        }
        audioSource.start()

        val textureManager = LAppTextureManager(context)
        val manager = LAppLive2DManager(textureManager)

        val audioMotion = AudioDrivenMotion(
            audioSource,
            AudioMotionConfig(
                enabled = prefs.getBoolean(FilterSettings.KEY_AUDIO_MOTION_ENABLED, true),
                intensity = prefs.getFloat(FilterSettings.KEY_AUDIO_MOTION_INTENSITY, 1.0f),
                speed = prefs.getFloat(FilterSettings.KEY_AUDIO_MOTION_SPEED, 1.0f)
            )
        )
        manager.setAudioDrivenMotion(audioMotion)

        val tiltSource = GyroscopeTiltSource(context)
        tiltSource.start()

        val gyroscopeMotion = GyroscopeDrivenMotion(tiltSource)
        manager.setGyroscopeDrivenMotion(gyroscopeMotion)

        return Live2DSession(textureManager, manager, audioSource, audioMotion, tiltSource, gyroscopeMotion)
    }

    fun destroy(session: Live2DSession) {
        session.tiltSource.stop()
        session.audioSource.stop()
        session.manager.releaseModel()
    }
}
