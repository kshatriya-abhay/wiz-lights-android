package io.github.kshatriyaabhay.wizlights.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.kshatriyaabhay.wizlights.widget.WidgetWork

/**
 * Debug builds only. Enqueues the same WidgetWork a widget tap does, so widget actions can be
 * tested from adb while the app is in the background:
 *   adb shell am broadcast -n io.github.kshatriyaabhay.wizlights/.debug.WidgetWorkReceiver --es op all_on
 * op: all_on, all_off, toggle (with --es mac <mac>), refresh, scan.
 */
class WidgetWorkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val op = intent.getStringExtra("op") ?: return
        WidgetWork.enqueue(context, op, intent.getStringExtra("mac"))
    }
}
