package com.example.liveai.agent

import android.util.Log

/**
 * Central toggle for agent system diagnostic logging.
 * Off by default — enable at runtime via [enabled] or at build time
 * via BuildConfig.DEBUG.
 */
object AgentDebug {

    @Volatile
    var enabled: Boolean = false

    inline fun log(tag: String, msg: () -> String) {
        if (enabled) Log.d(tag, msg())
    }
}
