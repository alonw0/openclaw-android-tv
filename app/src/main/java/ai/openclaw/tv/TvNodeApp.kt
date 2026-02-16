package ai.openclaw.tv

import android.app.Application
import android.os.StrictMode

/**
 * Application class for OpenClaw TV Node.
 * Initializes the TV-specific runtime and global state.
 */
class TvNodeApp : Application() {
  /** Lazy-initialized TV runtime that manages gateway connection and device capabilities */
  val runtime: TvNodeRuntime by lazy { TvNodeRuntime(this) }

  override fun onCreate() {
    super.onCreate()
    // Enable StrictMode for debugging in development builds
    if (BuildConfig.DEBUG) {
      StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .build(),
      )
      StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .build(),
      )
    }
  }
}
