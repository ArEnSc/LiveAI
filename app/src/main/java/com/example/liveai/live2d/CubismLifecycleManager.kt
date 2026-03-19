package com.example.liveai.live2d

import android.content.Context
import android.util.Log
import com.live2d.sdk.cubism.framework.CubismFramework

/**
 * Reference-counted singleton coordinator for CubismFramework static lifecycle.
 * Prevents multiple consumers (OverlayService, WallpaperSetupActivity, etc.)
 * from conflicting on cleanUp/startUp/dispose calls.
 */
object CubismLifecycleManager {

    private const val TAG = "CubismLifecycle"
    private var refCount = 0
    private val lock = Any()

    fun acquire(context: Context) {
        synchronized(lock) {
            if (refCount == 0) {
                Log.d(TAG, "First acquire — starting CubismFramework")
                LAppPal.setup(context.applicationContext)
                val option = CubismFramework.Option()
                option.logFunction = LAppPal.PrintLogFunction()
                option.loggingLevel = LAppDefine.cubismLoggingLevel
                CubismFramework.cleanUp()
                CubismFramework.startUp(option)
                LAppPal.updateTime()
            }
            refCount++
            Log.d(TAG, "acquire: refCount=$refCount")
        }
    }

    fun release() {
        synchronized(lock) {
            refCount--
            Log.d(TAG, "release: refCount=$refCount")
            if (refCount <= 0) {
                Log.d(TAG, "Last release — disposing CubismFramework")
                try {
                    CubismFramework.dispose()
                } catch (e: Exception) {
                    Log.w(TAG, "dispose failed (may already be disposed)", e)
                }
                refCount = 0
            }
        }
    }
}
