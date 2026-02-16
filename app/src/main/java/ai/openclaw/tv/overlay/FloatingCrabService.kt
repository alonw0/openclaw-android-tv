package ai.openclaw.tv.overlay

import ai.openclaw.tv.CrabEmotion
import ai.openclaw.tv.mascot.CrabMascot
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service that displays the crab mascot as a floating overlay on top of all apps.
 * 
 * Supports Android 11+ by creating a display context.
 * 
 * ⚠️ IMPORTANT SECURITY NOTICE:
 * This feature requires SYSTEM_ALERT_WINDOW permission which allows the app to draw over other apps.
 * Users must explicitly grant this permission in Settings.
 * The overlay should be clearly identifiable and not interfere with system security.
 */
class FloatingCrabService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var savedStateRegistryController: SavedStateRegistryController
    private var windowContext: Context? = null

    private val _emotion = MutableStateFlow(CrabEmotion.SLEEPING)
    val emotion: StateFlow<CrabEmotion> = _emotion.asStateFlow()
    
    // Notification message when agent needs attention
    private val _attentionMessage = MutableStateFlow<String?>(null)
    val attentionMessage: StateFlow<String?> = _attentionMessage.asStateFlow()
    
    // Callback for when crab is clicked while in attention mode
    private var onAttentionClicked: (() -> Unit)? = null

    companion object {
        private const val TAG = "FloatingCrabService"
        @Volatile
        var instance: FloatingCrabService? = null
            private set

        fun canDrawOverlays(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        fun requestPermission(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        fun start(context: Context) {
            if (!canDrawOverlays(context)) {
                requestPermission(context)
                return
            }
            val intent = Intent(context, FloatingCrabService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingCrabService::class.java)
            context.stopService(intent)
        }
        
        // Trigger attention mode from outside (e.g., from agent)
        fun notifyAttention(context: Context, message: String? = null) {
            instance?.let { service ->
                service._attentionMessage.value = message
                service._emotion.value = CrabEmotion.ATTENTION
                android.util.Log.d(TAG, "Attention notification: $message")
            }
        }
        
        // Clear attention mode
        fun clearAttention() {
            instance?.let { service ->
                service._attentionMessage.value = null
                service._emotion.value = CrabEmotion.SLEEPING
            }
        }
        
        // Set callback for when user clicks attention crab
        fun setAttentionCallback(callback: () -> Unit) {
            instance?.onAttentionClicked = callback
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        lifecycleRegistry = LifecycleRegistry(this)
        savedStateRegistryController = SavedStateRegistryController.create(this)
        // performAttach must be called BEFORE any lifecycle state changes (while still INITIALIZED)
        savedStateRegistryController.performAttach()
        // performRestore must be called before moving to CREATED state (Recreator needs this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        
        // Create a proper visual context for the window
        windowContext = createWindowContext()
        if (windowContext == null) {
            android.util.Log.e(TAG, "Failed to create window context")
            stopSelf()
            return
        }
        
        windowManager = windowContext!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingView()
        
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /**
     * Creates a proper visual context that can access WindowManager.
     * On Android 11+, we need to create a display context first, then a window context.
     */
    private fun createWindowContext(): Context? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+: Create a display context first
                val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                    ?: throw IllegalStateException("No default display found")
                
                // First create a display context from the service context
                val displayContext = createDisplayContext(display)
                    ?: throw IllegalStateException("Failed to create display context")
                
                // Then create a window context from the display context
                displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            } else {
                // Android 10 and below: Use the service context directly
                this
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to create window context: ${e.message}", e)
            null
        }
    }

    private fun createFloatingView() {
        val context = windowContext ?: return
        
        params = WindowManager.LayoutParams(
            600, // Wide to fit crab + large readable speech bubble
            220,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 24
        }

        // Create a ComposeView with the crab mascot using the window context
        val composeView = ComposeView(context).apply {
            setContent {
                val currentEmotion by emotion.collectAsState()
                val currentMessage by attentionMessage.collectAsState()
                CrabMascot(
                    emotion = currentEmotion,
                    size = 120, // Crab size - smaller than window to leave room for speech bubble
                    message = currentMessage,
                    onClick = {
                        // If in attention mode, trigger callback to open chat
                        if (currentEmotion == CrabEmotion.ATTENTION) {
                            onAttentionClicked?.invoke()
                            // Clear attention after click
                            _emotion.value = CrabEmotion.SLEEPING
                            _attentionMessage.value = null
                        } else {
                            // Normal click - open main app
                            val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                            } else {
                                android.util.Log.w(TAG, "Could not find launch intent for package")
                            }
                        }
                    }
                )
            }
        }

        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)
        floatingView = composeView
        windowManager?.addView(floatingView, params)
    }

    fun setEmotion(emotion: CrabEmotion) {
        _emotion.value = emotion
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Error removing floating view: ${e.message}")
            }
        }
        floatingView = null
        windowContext = null
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
        
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
}
