package space.globalintent.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class VisionCamera(
    private val activity: ComponentActivity,
    private val status: (String) -> Unit,
    private val onFrame: (Bitmap) -> Unit,
    private val onCapture: (File) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val smileDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()
    )
    private val smileGate = SmileGate()
    private val analyzingFaces = AtomicBoolean(false)
    @Volatile var autoSmileEnabled = false
    private var selector = CameraSelector.DEFAULT_BACK_CAMERA
    private var imageCapture: ImageCapture? = null
    private var attached: PreviewView? = null
    private var lastFrameAt = 0L

    fun attach(view: PreviewView) { attached = view; bind() }

    fun bind() {
        val view = attached ?: return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED) return
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().apply { surfaceProvider = view.surfaceProvider }
                val shutter = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { analyze(it) }
                provider.unbindAll()
                provider.bindToLifecycle(activity, selector, preview, shutter, analysis)
                imageCapture = shutter
                status("Camera ready. Enable Auto Smile to arm hands-free shutter.")
            } catch (e: Exception) { status("Camera unavailable: ${e.message}") }
        }, ContextCompat.getMainExecutor(activity))
    }

    fun flip() {
        selector = if (selector == CameraSelector.DEFAULT_BACK_CAMERA)
            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        smileGate.reset()
        bind()
    }

    fun setSmileMode(enabled: Boolean) {
        autoSmileEnabled = enabled
        smileGate.reset()
        status(if (enabled) "Auto Smile armed." else "Auto Smile off.")
    }

    private fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < 900L) { image.close(); return }
        lastFrameAt = now
        val bitmap = try { rgbaBitmap(image) } catch (e: Exception) {
            status("Frame error: ${e.message}")
            null
        } finally { image.close() } ?: return

        onFrame(bitmap)
        if (!autoSmileEnabled || !analyzingFaces.compareAndSet(false, true)) return
        smileDetector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces ->
                if (autoSmileEnabled) {
                    val probabilities = faces.map { it.smilingProbability }
                    val smiling = probabilities.count { it != null && it >= 0.75f }
                    status("Auto Smile: ${smiling}/${faces.size} detected faces smiling")
                    if (smileGate.sample(probabilities, now)) capture("smile")
                }
            }
            .addOnFailureListener {
                smileGate.reset()
                status("Smile detection failed: ${it.message}")
            }
            .addOnCompleteListener { analyzingFaces.set(false) }
    }

    fun capture(reason: String = "manual") {
        val shutter = imageCapture ?: run { status("Camera not ready"); return }
        val dir = File(activity.filesDir, "captures").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(dir, "intent_${reason}_${stamp}.jpg")
        shutter.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(activity), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    status("Saved private photo: ${file.name}")
                    onCapture(file)
                }
                override fun onError(exception: ImageCaptureException) {
                    status("Capture failed: ${exception.message}")
                }
            })
    }

    private fun rgbaBitmap(proxy: ImageProxy): Bitmap {
        val plane = proxy.planes[0]
        val buffer = plane.buffer
        val crop = proxy.cropRect
        val w = crop.width()
        val h = crop.height()
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val row = (y + crop.top) * plane.rowStride + crop.left * plane.pixelStride
            for (x in 0 until w) {
                val i = row + x * plane.pixelStride
                val a = buffer.get(i).toInt() and 255
                val r = buffer.get(i + 1).toInt() and 255
                val g = buffer.get(i + 2).toInt() and 255
                val b = buffer.get(i + 3).toInt() and 255
                pixels[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val raw = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        val degrees = proxy.imageInfo.rotationDegrees
        if (degrees == 0) return raw
        val rotated = Bitmap.createBitmap(raw, 0, 0, w, h,
            Matrix().apply { postRotate(degrees.toFloat()) }, true)
        if (rotated !== raw) raw.recycle()
        return rotated
    }

    fun close() {
        autoSmileEnabled = false
        smileDetector.close()
        executor.shutdown()
    }
}
