package ai.wasti.nexus

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Bitmap
import android.view.Display
import android.graphics.Rect
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Passive, user-enabled bridge to Android accessibility APIs.
 * It never scrapes events in the background or initiates tasks itself.
 */
class NexusAccessibilityService : AccessibilityService() {
    data class UiNode(
        val index: Int,
        val text: String,
        val contentDescription: String,
        val viewId: String,
        val className: String,
        val bounds: Rect,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val enabled: Boolean,
        val password: Boolean
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Deliberately passive. Callers explicitly request fresh UI state when needed.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    fun snapshot(maxNodes: Int = MAX_NODES): List<UiNode> {
        requireMainThread()
        val root = rootInActiveWindow ?: return emptyList()
        val result = ArrayList<UiNode>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        val limit = maxNodes.coerceIn(1, MAX_NODES)
        while (stack.isNotEmpty() && result.size < limit) {
            val node = stack.removeLast()
            val password = node.isPassword
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            result += UiNode(
                index = result.lastIndex + 1,
                text = if (password) "" else node.text?.toString().orEmpty().take(MAX_TEXT),
                contentDescription = if (password) "" else node.contentDescription?.toString().orEmpty().take(MAX_TEXT),
                viewId = node.viewIdResourceName.orEmpty().take(MAX_ID),
                className = node.className?.toString().orEmpty().take(MAX_ID),
                bounds = bounds,
                clickable = node.isClickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                enabled = node.isEnabled,
                password = password
            )
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let(stack::add)
        }
        return result
    }

    fun clickByText(label: String, exact: Boolean = true): Boolean {
        requireMainThread()
        require(label.isNotBlank() && label.length <= MAX_TEXT)
        val node = findNode { candidate ->
            val text = candidate.text?.toString().orEmpty()
            val description = candidate.contentDescription?.toString().orEmpty()
            candidate.isEnabled && candidate.isClickable &&
                if (exact) text.equals(label, true) || description.equals(label, true)
                else text.contains(label, true) || description.contains(label, true)
        } ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun setTextByViewId(viewId: String, value: String): Boolean {
        requireMainThread()
        require(viewId.isNotBlank() && viewId.length <= MAX_ID)
        require(value.length <= MAX_TEXT)
        // Never let a model or future tool executor write into password fields.
        // Credentials must be entered directly by the user in the target app.
        val node = findNode {
            it.isEnabled && it.isEditable && !it.isPassword && it.viewIdResourceName == viewId
        } ?: return false
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(direction: ScrollDirection, viewId: String? = null): Boolean {
        requireMainThread()
        val action = when (direction) {
            ScrollDirection.FORWARD -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            ScrollDirection.BACKWARD -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val node = findNode { it.isEnabled && it.isScrollable && (viewId == null || it.viewIdResourceName == viewId) }
            ?: return false
        return node.performAction(action)
    }

    fun tap(x: Float, y: Float): Boolean {
        requireMainThread()
        require(x.isFinite() && y.isFinite() && x >= 0f && y >= 0f)
        return dispatchPathGesture(x, y, x, y, 80L)
    }

    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 350L): Boolean {
        requireMainThread()
        require(listOf(fromX, fromY, toX, toY).all { it.isFinite() })
        require(fromX >= 0f && fromY >= 0f && toX >= 0f && toY >= 0f)
        require(durationMs in 80L..2_000L)
        return dispatchPathGesture(fromX, fromY, toX, toY, durationMs)
    }

    fun pressBack(): Boolean {
        requireMainThread()
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHome(): Boolean {
        requireMainThread()
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun openNotifications(): Boolean {
        requireMainThread()
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    /**
     * Capture one user-requested screenshot. Secure windows may be blank or unavailable.
     * The caller owns the returned bitmap and must recycle it after use.
     */
    @android.annotation.TargetApi(30)
    fun captureScreenshot(onResult: (Bitmap?) -> Unit) {
        requireMainThread()
        if (android.os.Build.VERSION.SDK_INT < 30) {
            onResult(null)
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val hardwareBuffer = screenshot.hardwareBuffer
                val bitmap = try {
                    runCatching {
                        Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    }.getOrNull()
                } finally {
                    hardwareBuffer.close()
                }
                onResult(bitmap)
            }

            override fun onFailure(errorCode: Int) {
                onResult(null)
            }
        })
    }

    private fun dispatchPathGesture(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun findNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited++ < MAX_NODES) {
            val node = stack.removeLast()
            if (predicate(node)) return node
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let(stack::add)
        }
        return null
    }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Accessibility actions must run on Android's main thread."
        }
    }

    enum class ScrollDirection { FORWARD, BACKWARD }

    companion object {
        private const val MAX_NODES = 600
        private const val MAX_TEXT = 500
        private const val MAX_ID = 200
        @Volatile private var activeService: NexusAccessibilityService? = null

        fun isConnected(): Boolean = activeService != null

        fun isEnabled(context: Context): Boolean {
            val expected = android.content.ComponentName(context, NexusAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                if (android.content.ComponentName.unflattenFromString(splitter.next()) == expected) return true
            }
            return false
        }

        fun current(): NexusAccessibilityService? = activeService
    }
}
