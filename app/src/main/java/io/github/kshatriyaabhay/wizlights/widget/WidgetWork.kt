package io.github.kshatriyaabhay.wizlights.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.kshatriyaabhay.wizlights.R
import io.github.kshatriyaabhay.wizlights.WizApp
import io.github.kshatriyaabhay.wizlights.net.DebugLog
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs a widget action as expedited work. A widget tap is handled while the
 * app is in the background, and Battery Saver blocks a background app's
 * network (sendto fails with EPERM). Expedited jobs are exempt from Battery
 * Saver, so the commands reach the bulbs.
 */
class WidgetWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = WizApp.of(applicationContext).repository
        repo.ensureLoaded()
        val op = inputData.getString(KEY_OP) ?: return Result.success()
        val mac = inputData.getString(KEY_MAC)
        DebugLog.d("widget work $op ${mac?.takeLast(4).orEmpty()}")
        withTimeoutOrNull(20_000) {
            when (op) {
                OP_ALL_ON -> repo.setAllPower(true)
                OP_ALL_OFF -> repo.setAllPower(false)
                OP_REFRESH -> repo.refreshNow()
                OP_SCAN -> repo.scan()
                OP_TOGGLE -> {
                    val l = repo.lights.value.firstOrNull { it.mac == mac }
                    when {
                        l == null -> Unit
                        !l.state.reachable -> repo.refreshNow()
                        else -> repo.setPower(l.mac, !l.state.on)
                    }
                }
            }
        }
        return Result.success()
    }

    /** Only used before Android 12, where expedited work runs as a foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Widget actions", NotificationManager.IMPORTANCE_MIN))
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_bulb)
            .setContentTitle("Updating lights")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        // Called only before Android 12 (minSdk 29-30), where service types are not enforced.
        return ForegroundInfo(NOTIFICATION_ID, n)
    }

    companion object {
        private const val KEY_OP = "op"
        private const val KEY_MAC = "mac"
        private const val CHANNEL = "widget-actions"
        private const val NOTIFICATION_ID = 7
        const val OP_ALL_ON = "all_on"
        const val OP_ALL_OFF = "all_off"
        const val OP_TOGGLE = "toggle"
        const val OP_REFRESH = "refresh"
        const val OP_SCAN = "scan"

        fun enqueue(context: Context, op: String, mac: String? = null) {
            // A network constraint is what makes JobScheduler open the network to a background
            // app's job under Battery Saver. Ask for Wi-Fi or Ethernet without requiring internet,
            // so the job runs on a LAN with no internet access too.
            val lan = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val req = OneTimeWorkRequestBuilder<WidgetWork>()
                .setConstraints(Constraints.Builder().setRequiredNetworkRequest(lan, NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(KEY_OP to op, KEY_MAC to mac))
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
