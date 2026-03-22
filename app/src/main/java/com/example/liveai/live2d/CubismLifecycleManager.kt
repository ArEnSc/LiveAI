package com.example.liveai.live2d

import android.content.Context
import android.util.Log
import com.live2d.sdk.cubism.framework.CubismFramework

/**
 * One-time initializer for CubismFramework. Call [ensureStarted] from any
 * consumer before loading models. The framework stays alive for the entire
 * process — no dispose/cleanUp/reinitialize ever needed.
 *
 * GL shader programs are per-thread (ThreadLocal in CubismShaderAndroid),
 * so multiple EGL contexts can coexist without conflict.
 */
object CubismLifecycleManager {

    private const val TAG = "CubismLifecycle"
    private var initialized = false
    private val lock = Any()

    /** Idempotent. Safe to call from any thread, any number of times. */
    fun ensureStarted(context: Context) {
        synchronized(lock) {
            if (initialized) return
            LAppPal.setup(context.applicationContext)
            val option = CubismFramework.Option()
            option.logFunction = LAppPal.PrintLogFunction()
            option.loggingLevel = LAppDefine.cubismLoggingLevel
            CubismFramework.startUp(option)
            CubismFramework.initialize()
            LAppPal.updateTime()
            initialized = true
            Log.d(TAG, "CubismFramework started and initialized (once)")
        }
    }
}
