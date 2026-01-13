package com.example.navhud

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class NotificationAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // Listen to both Google Maps and System UI (for notifications)
        if (packageName != "com.google.android.apps.maps" && packageName != "com.android.systemui") return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // Check the active window (could be Maps or the Notification Shade)
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    scanNodes(rootNode)
                }
                
                // Also check the specific node that triggered the event if it's a notification
                event.source?.let { scanNodes(it) }
            }
        }
    }

    private fun scanNodes(node: AccessibilityNodeInfo) {
        // Look for elements that might be navigation instructions
        val resourceName = node.viewIdResourceName ?: ""
        val contentDescription = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""

        // Filter for things that look like Maps data (titles, icons, distances)
        if (resourceName.contains("maps") || 
            resourceName.contains("notification") || 
            contentDescription.isNotEmpty()) {
            
            Log.d("AccessibilityMaps", "--- Found Element ---")
            Log.d("AccessibilityMaps", "Package: ${node.packageName}")
            Log.d("AccessibilityMaps", "ID: $resourceName")
            Log.d("AccessibilityMaps", "Text: $text")
            Log.d("AccessibilityMaps", "Desc: $contentDescription")
        }

        // Recursive scan
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { scanNodes(it) }
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        Log.d("AccessibilityMaps", "Service Connected - Scanning Maps and Notifications")
    }
}
