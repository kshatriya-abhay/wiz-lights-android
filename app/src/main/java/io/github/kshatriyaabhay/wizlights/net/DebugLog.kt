package io.github.kshatriyaabhay.wizlights.net

import android.util.Log
import io.github.kshatriyaabhay.wizlights.BuildConfig

/**
 * Logs in debug builds only. [d] takes a lambda and is inline, so release builds never build
 * the message string. Never log SSIDs in release builds.
 */
object DebugLog {
    const val TAG = "WizLights"

    inline fun d(msg: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg())
    }

    fun w(msg: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, msg, t)
    }
}
