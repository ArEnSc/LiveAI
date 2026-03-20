package com.example.liveai.live2d

import android.content.Context
import android.util.Log
import com.live2d.sdk.cubism.framework.CubismFramework
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Reference-counted singleton coordinator for CubismFramework static lifecycle.
 * Multiple consumers (OverlayService, WallpaperService, SetupActivity) can
 * coexist — each calls acquire/release independently. The framework is started
 * on first acquire and disposed on last release.
 *
 * Because CubismFramework is a process-wide singleton with static GL state,
 * only ONE EGL context can own it at a time. [forceReinitialize] tears down
 * and rebuilds the framework on the caller's GL context, invalidating all
 * other consumers. Use [generation] to detect when your context has been
 * superseded.
 *
 * The [frameworkLock] (read-write lock) prevents a draw call on one thread
 * from racing with forceReinitialize on another. Drawing takes a read lock;
 * forceReinitialize takes a write lock.
 */
object CubismLifecycleManager {

    private const val TAG = "CubismLifecycle"
    private var refCount = 0
    private var initialized = false
    private val lock = Any()

    /**
     * Read-write lock for Cubism framework access.
     * - Draw calls (onUpdate) take the READ lock — multiple can proceed in parallel.
     * - forceReinitialize takes the WRITE lock — blocks until all draws finish,
     *   and prevents new draws from starting during reinit.
     */
    val frameworkLock = ReentrantReadWriteLock()

    /**
     * Monotonically increasing counter bumped on every [forceReinitialize].
     * Consumers should snapshot this value after init and compare before
     * drawing — if it has changed, their GL resources are stale.
     */
    @Volatile
    var generation: Long = 0L
        private set

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
                initialized = false
            }
            refCount++
            Log.d(TAG, "acquire: refCount=$refCount")
        }
    }

    /**
     * Call from each GL thread's surface creation. CubismFramework.initialize()
     * sets up internal GL shader programs that are bound to the current EGL
     * context, so it MUST be called on every new GL context — not just once.
     */
    fun initialize() {
        synchronized(lock) {
            Log.d(TAG, "CubismFramework.initialize() on thread ${Thread.currentThread().name}")
            CubismFramework.initialize()
            initialized = true
        }
    }

    /**
     * Force a full framework teardown and re-setup on the current GL context.
     * Use this when a new EGL context replaces an old one (e.g. wallpaper engine
     * recreation) and the Cubism GL resources must be rebound.
     *
     * Acquires the WRITE lock to ensure no draw calls are in progress,
     * then increments [generation] so other consumers can detect staleness.
     */
    fun forceReinitialize(context: Context): Long {
        // Write lock ensures no draw call (which holds read lock) is in progress
        frameworkLock.writeLock().lock()
        try {
            synchronized(lock) {
                Log.d(TAG, "forceReinitialize — disposing and restarting framework (initialized=$initialized, refCount=$refCount, gen=$generation)")
                if (initialized) {
                    try {
                        CubismFramework.dispose()
                    } catch (e: Exception) {
                        Log.w(TAG, "dispose during forceReinitialize", e)
                    }
                }
                CubismFramework.cleanUp()
                LAppPal.setup(context.applicationContext)
                val option = CubismFramework.Option()
                option.logFunction = LAppPal.PrintLogFunction()
                option.loggingLevel = LAppDefine.cubismLoggingLevel
                CubismFramework.startUp(option)
                CubismFramework.initialize()
                initialized = true
                refCount = 1
                generation++
                LAppPal.updateTime()
                Log.d(TAG, "forceReinitialize complete, refCount=$refCount, gen=$generation")
                return generation
            }
        } finally {
            frameworkLock.writeLock().unlock()
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
                initialized = false
            }
        }
    }
}
