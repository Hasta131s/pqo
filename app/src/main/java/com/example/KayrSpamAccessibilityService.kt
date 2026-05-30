package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.*
import kotlin.math.roundToInt

// Simple Global State pattern
object KayrSpamState {
    var spamText by mutableStateOf("KayrSpam!")
    var intervalMs by mutableStateOf(300L)
    var isAltCase by mutableStateOf(false)
    var isAutoSend by mutableStateOf(true)
    var isSpamming by mutableStateOf(false)
}

class KayrSpamAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayContainer: FrameLayout? = null
    private var bubbleContainer: FrameLayout? = null
    
    private var overlayParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var spamJob: Job? = null
    
    // Lifecycle setup for using ComposeView in Service
    private val customLifecycleOwner = ServiceLifecycleOwner()

    companion object {
        var isServiceConnected = false
            private set
        var activeInstance: KayrSpamAccessibilityService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        customLifecycleOwner.onCreate()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        customLifecycleOwner.onStart()
        customLifecycleOwner.onResume()

        showFloatingBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Just keep the service alive and responsive to window changes
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceConnected = false
        activeInstance = null
        stopSpamming()
        removeFloatingViews()
        customLifecycleOwner.onPause()
        customLifecycleOwner.onStop()
        customLifecycleOwner.onDestroy()
        serviceScope.cancel()
    }

    fun startSpamming() {
        if (KayrSpamState.isSpamming) return
        KayrSpamState.isSpamming = true

        spamJob = serviceScope.launch {
            var altState = false
            while (KayrSpamState.isSpamming) {
                val originalText = KayrSpamState.spamText
                val textToType = if (KayrSpamState.isAltCase) {
                    altState = !altState
                    if (altState) {
                        originalText.uppercase()
                    } else {
                        originalText.lowercase()
                    }
                } else {
                    originalText
                }

                val rootNode = rootInActiveWindow
                val focusedNode = rootNode?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

                if (focusedNode != null) {
                    val arguments = Bundle()
                    arguments.putCharSequence("ACTION_ARG_SET_TEXT_CHARSEQUENCE", textToType)
                    focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

                    if (KayrSpamState.isAutoSend) {
                        delay(60) // Small yield for target UI input box to register text
                        val clickResult = clickSendButton(rootNode)
                        if (!clickResult) {
                            // If direct click failed, fallback to ACTION_SET_TEXT action or IME action
                            focusedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                    }
                } else {
                    // Quick fallback support: If findFocus(FOCUS_INPUT) returned null, recursively scan for any active focused EditText
                    val fallbackNode = findAnyFocusedEditText(rootNode)
                    if (fallbackNode != null) {
                        val arguments = Bundle()
                        arguments.putCharSequence("ACTION_ARG_SET_TEXT_CHARSEQUENCE", textToType)
                        fallbackNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        
                        if (KayrSpamState.isAutoSend) {
                            delay(60)
                            clickSendButton(rootNode)
                        }
                        fallbackNode.recycle()
                    }
                }

                rootNode?.recycle()
                focusedNode?.recycle()
                
                delay(KayrSpamState.intervalMs)
            }
        }
    }

    fun stopSpamming() {
        KayrSpamState.isSpamming = false
        spamJob?.cancel()
        spamJob = null
    }

    private fun findAnyFocusedEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused && (node.className?.contains("EditText") == true || node.isEditable)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            val result = findAnyFocusedEditText(child)
            child?.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun clickSendButton(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val className = node.className?.toString() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val isSendButton = node.isClickable && (
            text.contains("gönder") || text.contains("yolla") || text.contains("send") || text.contains("paylaş") ||
            desc.contains("gönder") || desc.contains("yolla") || desc.contains("send") || desc.contains("paylaş") ||
            viewId.contains("send") || viewId.contains("submit") || viewId.contains("gonder") ||
            (className.contains("ImageView") || className.contains("Button")) && (
                desc.contains("gönder") || desc.contains("send") || desc.contains("action_send") || viewId.contains("send")
            )
        )

        if (isSendButton) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (clickSendButton(child)) {
                child?.recycle()
                return true
            }
            child?.recycle()
        }
        return false
    }

    private fun removeFloatingViews() {
        try {
            overlayContainer?.let { windowManager?.removeView(it) }
            bubbleContainer?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        overlayContainer = null
        bubbleContainer = null
    }

    // Displays the floating trigger handle button
    private fun showFloatingBubble() {
        if (windowManager == null) return
        removeFloatingViews()

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 400
        }

        bubbleContainer = FrameLayout(this)
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(customLifecycleOwner)
            setViewTreeViewModelStoreOwner(customLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(customLifecycleOwner)
            setContent {
                KayrFloatingBubble(
                    onExpandRequested = {
                        showFloatingControlPanel()
                    }
                )
            }
        }
        bubbleContainer?.addView(composeView)

        // Make the bubble draggable using system touch dynamics
        bubbleContainer?.setOnTouchListener(object : View.OnTouchListener {
            private var lastX = 0
            private var lastY = 0
            private var rawX = 0f
            private var rawY = 0f
            private var isDrag = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = bubbleParams?.x ?: 0
                        lastY = bubbleParams?.y ?: 0
                        rawX = event.rawX
                        rawY = event.rawY
                        isDrag = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - rawX).toInt()
                        val dy = (event.rawY - rawY).toInt()
                        if (dx * dx + dy * dy > 64) {
                            isDrag = true
                        }
                        bubbleParams?.let { params ->
                            params.x = lastX + dx
                            params.y = lastY + dy
                            windowManager?.updateViewLayout(bubbleContainer, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDrag) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        bubbleContainer?.setOnClickListener {
            showFloatingControlPanel()
        }

        try {
            windowManager?.addView(bubbleContainer, bubbleParams)
        } catch (_: Exception) {}
    }

    // Opens the complete text input, slider and settings panel right above the standard screen keyboard overlay
    private fun showFloatingControlPanel() {
        if (windowManager == null) return
        
        // Hide the bubble trigger button while panel is open
        bubbleContainer?.visibility = View.GONE

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 120 // Positioned elegant, floating just above standard soft keyboard boundary
        }

        overlayContainer = FrameLayout(this)
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(customLifecycleOwner)
            setViewTreeViewModelStoreOwner(customLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(customLifecycleOwner)
            setContent {
                KayrSpamOverlayPanel(
                    onMinimizeRequested = {
                        hideControlPanel()
                    },
                    onRequestInputFocus = { needFocus ->
                        toggleOverlayFocus(needFocus)
                    },
                    onStartSpam = {
                        startSpamming()
                    },
                    onStopSpam = {
                        stopSpamming()
                    }
                )
            }
        }
        overlayContainer?.addView(composeView)

        try {
            windowManager?.addView(overlayContainer, overlayParams)
        } catch (e: Exception) {
            // Fallback show bubble if fails
            bubbleContainer?.visibility = View.VISIBLE
        }
    }

    private fun hideControlPanel() {
        overlayContainer?.let { container ->
            try {
                windowManager?.removeView(container)
            } catch (_: Exception) {}
        }
        overlayContainer = null
        bubbleContainer?.visibility = View.VISIBLE
    }

    // Toggle WindowManager key focus so user can type on our layout's EditText without blocking underlying keyboard clicks
    private fun toggleOverlayFocus(needFocus: Boolean) {
        val params = overlayParams ?: return
        val container = overlayContainer ?: return
        
        if (needFocus) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        
        try {
            windowManager?.updateViewLayout(container, params)
        } catch (_: java.lang.Exception) {}
    }
}

// Minimal Lifecycle components integration for embedding Compose elements onto standard WindowManager service
class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onPause() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onStop() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}

// Beautiful cyber theme bubble design
@Composable
fun KayrFloatingBubble(onExpandRequested: () -> Unit) {
    val isSpamming = KayrSpamState.isSpamming
    val pulseScale by animateFloatAsState(
        targetValue = if (isSpamming) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 150f)
    )

    Box(
        modifier = Modifier
            .scale(pulseScale)
            .size(54.dp)
            .shadow(8.dp, CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isSpamming) {
                        listOf(Color(0xFFE53935), Color(0xFFB71C1C)) // Burning Red for active spamming
                    } else {
                        listOf(Color(0xFFD0BCFF), Color(0xFF4F378B)) // Elegant Royal Violet for idle alert
                    }
                ),
                shape = CircleShape
            )
            .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            .clickable { onExpandRequested() },
        contentAlignment = Alignment.Center
    ) {
        if (isSpamming) {
            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = "Spamming Active",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        } else {
            Text(
                text = "K",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Complete Overlay controller panel loaded directly dynamically in the Accessibility Window View
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KayrSpamOverlayPanel(
    onMinimizeRequested: () -> Unit,
    onRequestInputFocus: (Boolean) -> Unit,
    onStartSpam: () -> Unit,
    onStopSpam: () -> Unit
) {
    var isInputFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xEC1C1B1F) // Deep elegant dark surface with opacity
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag / Header bar controller
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (KayrSpamState.isSpamming) Color(0xFFE53935) else Color(0xFFD0BCFF),
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = "KayrSpam Controller",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                
                IconButton(
                    onClick = { onMinimizeRequested() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Minimize",
                        tint = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Spam message writing input block
            OutlinedTextField(
                value = KayrSpamState.spamText,
                onValueChange = { KayrSpamState.spamText = it },
                label = { Text("Spam Edilecek Yazı", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                    focusedBorderColor = Color(0xFFD0BCFF),
                    unfocusedBorderColor = Color.DarkGray
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Keyboard Input",
                        tint = Color(0xFFD0BCFF)
                    )
                },
                trailingIcon = {
                    if (KayrSpamState.spamText.isNotEmpty()) {
                        IconButton(onClick = { KayrSpamState.spamText = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Text",
                                tint = Color.LightGray
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(),
                singleLine = true,
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        isInputFocused = false
                        onRequestInputFocus(false)
                    }
                )
            )

            // Dynamic tracking handle status context
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = if (isInputFocused) "[Klavye Odaklandı]" else "Klavyeyi Odakla",
                    color = if (isInputFocused) Color(0xFFD0BCFF) else Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        isInputFocused = !isInputFocused
                        onRequestInputFocus(isInputFocused)
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Speed Interval bar settings slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Yazma Hızı: ${KayrSpamState.intervalMs}ms",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val presets = listOf(100L, 300L, 1000L)
                    presets.forEach { ms ->
                        Text(
                            text = "${ms}ms",
                            color = if (KayrSpamState.intervalMs == ms) Color(0xFFD0BCFF) else Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    color = if (KayrSpamState.intervalMs == ms) Color(0xFFD0BCFF).copy(alpha = 0.2f) else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clickable { KayrSpamState.intervalMs = ms }
                        )
                    }
                }
            }

            Slider(
                value = KayrSpamState.intervalMs.toFloat(),
                onValueChange = { KayrSpamState.intervalMs = it.toLong() },
                valueRange = 50f..2000f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD0BCFF),
                    activeTrackColor = Color(0xFFD0BCFF),
                    inactiveTrackColor = Color.DarkGray
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Switch settings: Shift-Alternate effect and Auto Send toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Alternating capitalisation cycle option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = KayrSpamState.isAltCase,
                        onCheckedChange = { KayrSpamState.isAltCase = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFD0BCFF),
                            checkmarkColor = Color(0xFF381E72)
                        )
                    )
                    Column {
                        Text("Shift Etkisi", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("AŞIRI/normal", color = Color.Gray, fontSize = 10.sp)
                    }
                }

                // Auto Send click action option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = KayrSpamState.isAutoSend,
                        onCheckedChange = { KayrSpamState.isAutoSend = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFD0BCFF),
                            checkmarkColor = Color(0xFF381E72)
                        )
                    )
                    Column {
                        Text("Otomatik Gönder", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Otomatik Tıklar", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Start/Stop toggle button
            Button(
                onClick = {
                    if (KayrSpamState.isSpamming) {
                        onStopSpam()
                    } else {
                        onStartSpam()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (KayrSpamState.isSpamming) Color(0xFFE53935) else Color(0xFFD0BCFF)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = if (KayrSpamState.isSpamming) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (KayrSpamState.isSpamming) Color.White else Color(0xFF381E72)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (KayrSpamState.isSpamming) "SPAMMING DURDUR" else "SPAMMING BAŞLAT",
                    color = if (KayrSpamState.isSpamming) Color.White else Color(0xFF381E72),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
