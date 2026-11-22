package com.example.phoneagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PhoneService : AccessibilityService() {
    companion object { @Volatile var inst: PhoneService? = null }

    override fun onServiceConnected() { inst = this }
    override fun onUnbind(intent: Intent?): Boolean { inst = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun screenshot(): Bitmap? {
        var out: Bitmap? = null
        val latch = CountDownLatch(1)
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                out = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                r.hardwareBuffer.close()
                latch.countDown()
            }
            override fun onFailure(errorCode: Int) { latch.countDown() }
        })
        latch.await(5, TimeUnit.SECONDS)
        return out
    }

    fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val latch = CountDownLatch(1)
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build()
        dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { latch.countDown() }
            override fun onCancelled(d: GestureDescription?) { latch.countDown() }
        }, null)
        latch.await(5, TimeUnit.SECONDS)
    }

    fun typeText(t: String): Boolean {
        val n = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t) }
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    fun openApp(name: String): Boolean {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val app = pm.queryIntentActivities(q, 0)
            .firstOrNull { it.loadLabel(pm).toString().contains(name, ignoreCase = true) } ?: return false
        val i = pm.getLaunchIntentForPackage(app.activityInfo.packageName) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        return true
    }
}
