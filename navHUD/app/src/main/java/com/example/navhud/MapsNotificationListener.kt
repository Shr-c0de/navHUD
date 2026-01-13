package com.example.navhud

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MapsNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != "com.google.android.apps.maps") return

        try {
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            
            // Handle Icon vs Bitmap carefully to avoid ClassCastException
            val bitmap = getNotificationBitmap(notification)

            Log.d("MapsListener", "Received: $title - $text")
            
            NavigationRepository.update(
                instruction = title,
                details = text,
                icon = bitmap
            )
        } catch (e: Exception) {
            Log.e("MapsListener", "Error parsing notification", e)
        }
    }

    private fun getNotificationBitmap(notification: Notification): Bitmap? {
        val extras = notification.extras
        
        // Try getting the icon object first (Modern Android)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val icon = notification.getLargeIcon() ?: extras.getParcelable<Icon>(Notification.EXTRA_LARGE_ICON)
            if (icon != null) {
                return iconToBitmap(icon)
            }
        }

        // Fallback for older versions or if icon is stored directly as Bitmap
        @Suppress("DEPRECATION")
        val parcelable = extras.get(Notification.EXTRA_LARGE_ICON)
        if (parcelable is Bitmap) {
            return parcelable
        }
        
        return null
    }

    private fun iconToBitmap(icon: Icon): Bitmap? {
        try {
            val drawable = icon.loadDrawable(this) ?: return null
            if (drawable is BitmapDrawable) {
                return drawable.bitmap
            }
            
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        } catch (e: Exception) {
            Log.e("MapsListener", "Failed to convert Icon to Bitmap", e)
            return null
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName == "com.google.android.apps.maps") {
            NavigationRepository.update("No Data", "Navigation Stopped", null)
        }
    }
}
