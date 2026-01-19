package aer.app.navhud

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class MapsNotificationListener : NotificationListenerService() {

    private lateinit var bleManager: BleManager
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var navigationStopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        bleManager = (application as NavHudApplication).bleManager

        // Observe the READY state to send data immediately upon connection and service discovery
        serviceScope.launch {
            bleManager.isReady.collect { isReady ->
                if (isReady) {
                    Log.d("MapsListener", "BLE is ready. Checking for pending navigation data...")
                    val currentNavInfo = NavigationRepository.navInfo.value
                    if (currentNavInfo.isNavigating) {
                        Log.d("MapsListener", "Pending data found. Sending upon connection.")
                        val data = serializeForBle(currentNavInfo.instruction, currentNavInfo.details, currentNavInfo.subText, currentNavInfo.icon)
                        bleManager.sendData(data)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // Clean up the coroutine
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName == "com.google.android.apps.maps") {
            navigationStopJob?.cancel()
            processNotification(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName == "com.google.android.apps.maps") {
            navigationStopJob = serviceScope.launch {
                delay(2000L)
                Log.d("MapsListener", "Navigation stop delay finished. Sending Standby.")
                NavigationRepository.setNavigationStopped()
                if (bleManager.isConnected.value) {
                    val standbyData = serializeForBle("Standby State", "", "", null)
                    bleManager.sendData(standbyData)
                }
            }
        }
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        getActiveNotifications()?.firstOrNull { it.packageName == "com.google.android.apps.maps" }?.let {
            navigationStopJob?.cancel()
            processNotification(it)
        }
    }

    private fun toAsciiSafe(input: String?): String? {
        return input
            ?.replace('·', '|')
            ?.replace(Regex("[^\\x20-\\x7E]"), "")
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = toAsciiSafe(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        val text = toAsciiSafe(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        val subText = toAsciiSafe(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
        val bitmap = getNotificationBitmap(notification)

        Log.d("MapsListener", "Auto-Update: $title - $text - $subText")

        val fullData = serializeForBle(title, text, subText, bitmap)
        bleManager.sendData(fullData)

        NavigationRepository.update(instruction = title, details = text, subText = subText, icon = bitmap)
    }

    private fun serializeForBle(title: String?, details: String?, subText: String?, bitmap: Bitmap?): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val fieldDelimiter = "|".toByteArray()
        val endOfMessage = "\n".toByteArray()

        if (title != null) outputStream.write(title.toByteArray())
        outputStream.write(fieldDelimiter)

        if (details != null) outputStream.write(details.toByteArray())
        outputStream.write(fieldDelimiter)

        if (subText != null) outputStream.write(subText.toByteArray())
        outputStream.write(fieldDelimiter)

        if (bitmap != null) {
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
            val monochromeData = ByteArray(48 * 48 / 8)
            for (i in monochromeData.indices) {
                var currentByte = 0
                for (j in 0..7) {
                    val pixelIndex = i * 8 + j
                    val x = pixelIndex % 48
                    val y = pixelIndex / 48
                    if (scaledBitmap.getPixel(x, y) ushr 24 > 128) {
                        currentByte = currentByte or (1 shl (7 - j))
                    }
                }
                monochromeData[i] = currentByte.toByte()
            }
            outputStream.write(monochromeData)
        }

        outputStream.write(endOfMessage)

        return outputStream.toByteArray()
    }

    private fun getNotificationBitmap(notification: Notification): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val icon = notification.getLargeIcon()
            if (icon != null) return iconToBitmap(icon)
        }
        @Suppress("DEPRECATION")
        val bitmap = notification.extras.get(Notification.EXTRA_LARGE_ICON)
        return if (bitmap is Bitmap) bitmap else null
    }

    private fun iconToBitmap(icon: Icon): Bitmap? {
        try {
            val drawable = icon.loadDrawable(this) ?: return null
            if (drawable is BitmapDrawable) return drawable.bitmap
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        } catch (e: Exception) { return null }
    }
}
