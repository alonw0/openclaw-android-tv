package ai.openclaw.tv.node

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Base64
import android.view.Surface
import ai.openclaw.tv.ScreenCaptureRequester
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToInt

class ScreenRecordManager(private val context: Context) {
  data class Payload(val payloadJson: String)
  data class CaptureResult(val resultCode: Int, val data: Intent)

  @Volatile private var screenCaptureRequester: ScreenCaptureRequester? = null
  @Volatile private var permissionRequester: ai.openclaw.tv.PermissionRequester? = null
  @Volatile private var cachedProjection: android.media.projection.MediaProjection? = null

  fun attachScreenCaptureRequester(requester: ScreenCaptureRequester) {
    screenCaptureRequester = requester
  }

  fun attachPermissionRequester(requester: ai.openclaw.tv.PermissionRequester) {
    permissionRequester = requester
  }

  private suspend fun getOrCreateProjection(): android.media.projection.MediaProjection? {
    // Check if we have a cached projection that's still valid
    cachedProjection?.let { projection ->
      try {
        // Try to use it - if it's stopped, this will fail
        return projection
      } catch (_: Exception) {
        cachedProjection = null
      }
    }

    // Need to request new permission via Activity
    val requester = screenCaptureRequester
      ?: throw IllegalStateException("SCREEN_PERMISSION_REQUIRED: grant Screen Recording permission (open TV app first)")

    val capture = requester.requestCapture()
      ?: throw IllegalStateException("SCREEN_PERMISSION_REQUIRED: grant Screen Recording permission")

    val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val projection = mgr.getMediaProjection(capture.resultCode, capture.data)
      ?: throw IllegalStateException("UNAVAILABLE: screen capture unavailable")

    // Register callback and cache it
    projection.registerCallback(object : android.media.projection.MediaProjection.Callback() {
      override fun onStop() {
        android.util.Log.d("ScreenRecordManager", "MediaProjection stopped")
        cachedProjection = null
      }
    }, null)

    cachedProjection = projection
    return projection
  }

  suspend fun record(paramsJson: String?): Payload =
    withContext(Dispatchers.Default) {
      val projection = getOrCreateProjection()
        ?: throw IllegalStateException("UNAVAILABLE: screen capture unavailable")

      val durationMs = (parseDurationMs(paramsJson) ?: 10_000).coerceIn(250, 60_000)
      val fps = (parseFps(paramsJson) ?: 10.0).coerceIn(1.0, 60.0)
      val fpsInt = fps.roundToInt().coerceIn(1, 60)
      val screenIndex = parseScreenIndex(paramsJson)
      val includeAudio = parseIncludeAudio(paramsJson) ?: true
      val format = parseString(paramsJson, key = "format")
      if (format != null && format.lowercase() != "mp4") {
        throw IllegalArgumentException("INVALID_REQUEST: screen format must be mp4")
      }
      if (screenIndex != null && screenIndex != 0) {
        throw IllegalArgumentException("INVALID_REQUEST: screenIndex must be 0 on Android")
      }

      val metrics = context.resources.displayMetrics
      val width = metrics.widthPixels
      val height = metrics.heightPixels
      val densityDpi = metrics.densityDpi

      val file = File.createTempFile("openclaw-screen-", ".mp4")
      if (includeAudio) ensureMicPermission()

      val recorder = createMediaRecorder()
      var virtualDisplay: android.hardware.display.VirtualDisplay? = null
      try {
        if (includeAudio) {
          recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        }
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        if (includeAudio) {
          recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
          recorder.setAudioChannels(1)
          recorder.setAudioSamplingRate(44_100)
          recorder.setAudioEncodingBitRate(96_000)
        }
        recorder.setVideoSize(width, height)
        recorder.setVideoFrameRate(fpsInt)
        recorder.setVideoEncodingBitRate(estimateBitrate(width, height, fpsInt))
        recorder.setOutputFile(file.absolutePath)
        recorder.prepare()

        val surface = recorder.surface
        virtualDisplay =
          projection.createVirtualDisplay(
            "openclaw-screen",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null,
          )

        recorder.start()
        delay(durationMs.toLong())
      } finally {
        try {
          recorder.stop()
        } catch (_: Throwable) {
          // ignore
        }
        recorder.reset()
        recorder.release()
        virtualDisplay?.release()
        projection.stop()
      }

      val returnData = parseReturnData(paramsJson) ?: false
      
      val response = if (returnData) {
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        """{"format":"mp4","base64":"$base64","durationMs":$durationMs,"fps":$fpsInt,"screenIndex":0,"hasAudio":$includeAudio}"""
      } else {
        file.delete()
        """{"recorded":true,"format":"mp4","durationMs":$durationMs,"fps":$fpsInt,"screenIndex":0,"hasAudio":$includeAudio}"""
      }
      Payload(response)
    }

  private fun createMediaRecorder(): MediaRecorder = MediaRecorder(context)

  private suspend fun ensureMicPermission() {
    val granted =
      androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO,
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (granted) return

    val requester =
      permissionRequester
        ?: throw IllegalStateException("MIC_PERMISSION_REQUIRED: grant Microphone permission")
    val results = requester.requestIfMissing(listOf(android.Manifest.permission.RECORD_AUDIO))
    if (results[android.Manifest.permission.RECORD_AUDIO] != true) {
      throw IllegalStateException("MIC_PERMISSION_REQUIRED: grant Microphone permission")
    }
  }

  private fun parseDurationMs(paramsJson: String?): Int? =
    parseNumber(paramsJson, key = "durationMs")?.toIntOrNull()

  private fun parseFps(paramsJson: String?): Double? =
    parseNumber(paramsJson, key = "fps")?.toDoubleOrNull()

  private fun parseScreenIndex(paramsJson: String?): Int? =
    parseNumber(paramsJson, key = "screenIndex")?.toIntOrNull()

  private fun parseIncludeAudio(paramsJson: String?): Boolean? {
    val raw = paramsJson ?: return null
    val key = "\"includeAudio\""
    val idx = raw.indexOf(key)
    if (idx < 0) return null
    val colon = raw.indexOf(':', idx + key.length)
    if (colon < 0) return null
    val tail = raw.substring(colon + 1).trimStart()
    return when {
      tail.startsWith("true") -> true
      tail.startsWith("false") -> false
      else -> null
    }
  }

  private fun parseReturnData(paramsJson: String?): Boolean? {
    val raw = paramsJson ?: return null
    val key = "\"returnData\""
    val idx = raw.indexOf(key)
    if (idx < 0) return null
    val colon = raw.indexOf(':', idx + key.length)
    if (colon < 0) return null
    val tail = raw.substring(colon + 1).trimStart()
    return when {
      tail.startsWith("true") -> true
      tail.startsWith("false") -> false
      else -> null
    }
  }

  private fun parseNumber(paramsJson: String?, key: String): String? {
    val raw = paramsJson ?: return null
    val needle = "\"$key\""
    val idx = raw.indexOf(needle)
    if (idx < 0) return null
    val colon = raw.indexOf(':', idx + needle.length)
    if (colon < 0) return null
    val tail = raw.substring(colon + 1).trimStart()
    return tail.takeWhile { it.isDigit() || it == '.' || it == '-' }
  }

  private fun parseString(paramsJson: String?, key: String): String? {
    val raw = paramsJson ?: return null
    val needle = "\"$key\""
    val idx = raw.indexOf(needle)
    if (idx < 0) return null
    val colon = raw.indexOf(':', idx + needle.length)
    if (colon < 0) return null
    val tail = raw.substring(colon + 1).trimStart()
    if (!tail.startsWith('\"')) return null
    val rest = tail.drop(1)
    val end = rest.indexOf('\"')
    if (end < 0) return null
    return rest.substring(0, end)
  }

  private fun estimateBitrate(width: Int, height: Int, fps: Int): Int {
    val pixels = width.toLong() * height.toLong()
    val raw = (pixels * fps.toLong() * 2L).toInt()
    return raw.coerceIn(1_000_000, 12_000_000)
  }

  suspend fun snapshot(paramsJson: String?): Payload =
    withContext(Dispatchers.Default) {
      val projection = getOrCreateProjection()
        ?: throw IllegalStateException("UNAVAILABLE: screen capture unavailable")

      val format = parseString(paramsJson, key = "format")?.lowercase() ?: "png"
      if (format !in setOf("png", "jpeg")) {
        throw IllegalArgumentException("INVALID_REQUEST: format must be png or jpeg")
      }
      val quality = parseNumber(paramsJson, key = "quality")?.toIntOrNull()?.coerceIn(1, 100) ?: 85
      val maxWidth = parseNumber(paramsJson, key = "maxWidth")?.toIntOrNull()

      delay(1000)

      val metrics = context.resources.displayMetrics
      val width = metrics.widthPixels
      val height = metrics.heightPixels
      val densityDpi = metrics.densityDpi

      val targetWidth = maxWidth?.coerceAtLeast(1) ?: width
      val targetHeight = if (maxWidth != null && maxWidth < width) {
        (height * maxWidth.toFloat() / width.toFloat()).toInt().coerceAtLeast(1)
      } else {
        height
      }

      val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
      var virtualDisplay: android.hardware.display.VirtualDisplay? = null
      var bitmap: Bitmap? = null

      try {
        val image = suspendCancellableCoroutine { cont ->
          val listener =
            ImageReader.OnImageAvailableListener { reader ->
              try {
                val img = reader.acquireLatestImage()
                if (img != null && cont.isActive) {
                  cont.resume(img)
                } else {
                  img?.close()
                }
              } catch (_: Throwable) {
              }
            }
          // Use main thread handler for ImageReader callback
          val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
          imageReader.setOnImageAvailableListener(listener, mainHandler)

          virtualDisplay =
            projection.createVirtualDisplay(
              "openclaw-snapshot",
              width,
              height,
              densityDpi,
              DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
              imageReader.surface,
              null,
              null,
            )

          cont.invokeOnCancellation {
            imageReader.setOnImageAvailableListener(null, null)
          }

          kotlinx.coroutines.runBlocking { delay(500) }
        }

        bitmap =
          try {
            val buffer = image.planes[0].buffer
            val pixelStride = image.planes[0].pixelStride
            val rowStride = image.planes[0].rowStride
            val rowPadding = rowStride - (pixelStride * width)
            val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)

            if (width != targetWidth || height != targetHeight) {
              Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also {
                if (it !== bitmap) bitmap.recycle()
              }
            } else {
              bitmap
            }
          } finally {
            image.close()
          }
      } finally {
        virtualDisplay?.release()
        projection.stop()
        imageReader.close()
      }

      val bytes =
        withContext(Dispatchers.IO) {
          ByteArrayOutputStream().use { stream ->
            when (format) {
              "jpeg" -> {
                bitmap!!.compress(Bitmap.CompressFormat.JPEG, quality, stream)
              }
              else -> {
                bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, stream)
              }
            }
            bitmap.recycle()
            stream.toByteArray()
          }
        }

      val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
      Payload(
        """{"format":"$format","base64":"$base64","width":$targetWidth,"height":$targetHeight,"screenIndex":0}""",
      )
    }
}
