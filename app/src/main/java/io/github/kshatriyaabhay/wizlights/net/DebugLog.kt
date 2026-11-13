package io.github.kshatriyaabhay.wizlights.net

import android.util.Log
import io.github.kshatriyaabhay.wizlights.BuildConfig

/** Logs in debug builds only. Never log SSIDs in release builds. */
object DebugLog {
    private const val TAG = "WizLights"

    fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    fun w(msg: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, msg, t)
    }
}
