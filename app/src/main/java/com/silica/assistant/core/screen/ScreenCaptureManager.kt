package com.silica.assistant.core.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream

object ScreenCaptureManager {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    var resultCode: Int = Activity.RESULT_CANCELED
    var resultData: Intent? = null

    private var displayWidth = 0
    private var displayHeight = 0
    private var displayDensity = 0

    private val captureHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(metrics)
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels
        displayDensity = metrics.densityDpi
    }

    fun setupProjection(context: Context) {
        try {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            if (resultCode == Activity.RESULT_OK && resultData != null) {
                mediaProjection = mgr.getMediaProjection(resultCode, resultData!!)
                startCapture()
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreenCapture", "setupProjection failed", e)
            mediaProjection = null
        }
    }

    fun tryRestore(context: Context): Boolean {
        android.util.Log.d("ScreenCapture", "tryRestore: mp=$mediaProjection ir=$imageReader rc=$resultCode rd=$resultData")
        if (mediaProjection != null && imageReader == null) {
            android.util.Log.d("ScreenCapture", "tryRestore: mediaProjection alive, re-creating capture")
            startCapture()
        }
        if (resultCode == Activity.RESULT_OK && resultData != null && !isReady()) {
            android.util.Log.d("ScreenCapture", "tryRestore: attempting setupProjection from saved intent")
            setupProjection(context)
        }
        val ready = isReady()
        android.util.Log.d("ScreenCapture", "tryRestore: ready=$ready mp=$mediaProjection ir=$imageReader")
        return ready
    }

    fun isReady(): Boolean = mediaProjection != null && imageReader != null

    private var captureThread: android.os.HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private fun startCapture() {
        try {
            if (captureThread == null) {
                captureThread = android.os.HandlerThread("ScreenCapture").apply { start() }
                backgroundHandler = Handler(captureThread!!.looper)
            }

            imageReader?.close()
            imageReader = ImageReader.newInstance(
                displayWidth, displayHeight,
                android.graphics.PixelFormat.RGBA_8888, 2
            )

            virtualDisplay?.release()
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                displayWidth, displayHeight, displayDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, backgroundHandler
            )
        } catch (e: Exception) {
            android.util.Log.e("ScreenCapture", "startCapture failed", e)
            imageReader?.close()
            imageReader = null
        }
    }

    fun captureNow(): Bitmap? {
        try {
            val reader = imageReader ?: return null
            val image = reader.acquireLatestImage() ?: return null

            val planes = image.planes[0]
            val buffer = planes.buffer
            val pixelStride = planes.pixelStride
            val rowStride = planes.rowStride
            val w = image.width
            val h = image.height

            val temp = Bitmap.createBitmap(
                w + (rowStride - pixelStride * w) / pixelStride,
                h, Bitmap.Config.ARGB_8888
            )
            temp.copyPixelsFromBuffer(buffer)
            image.close()

            val result = if (temp.width > w) {
                Bitmap.createBitmap(temp, 0, 0, w, h)
            } else temp
            if (result !== temp) temp.recycle()
            return result
        } catch (e: Exception) {
            android.util.Log.e("ScreenCapture", "captureNow failed", e)
            return null
        }
    }

    fun captureScaledJpeg(maxPixels: Int = 800): ByteArray? {
        try {
            val bitmap = captureNow() ?: return null
            val scale = minOf(1f, maxPixels.toFloat() / maxOf(bitmap.width, bitmap.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else bitmap

            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            val bytes = stream.toByteArray()

            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()

            return bytes
        } catch (e: Exception) {
            android.util.Log.e("ScreenCapture", "captureScaledJpeg failed", e)
            return null
        }
    }

    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        captureThread?.quitSafely()
        captureThread = null
        backgroundHandler = null
    }
}
