package com.malithyst.mysecurity

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleService
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MySecurityService : LifecycleService() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "my_security_channel_foreground_v3"
        const val NOTIFICATION_ID = 1
        private const val TAG = "MySecurityService"
    }

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isCapturing = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received broadcast: ${intent?.action}")
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                Log.d(TAG, "Device unlocked!")
                onUserUnlocked()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")

        cameraExecutor = Executors.newSingleThreadExecutor()

        createNotificationChannel()
        val notification = buildNotification()
        Log.d(TAG, "Calling startForeground()")
        startForeground(NOTIFICATION_ID, notification)

        registerUnlockReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand(): startId=$startId, intent=$intent")
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Receiver not registered", e)
        }

        cameraExecutor.shutdown()
        releaseCamera()
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    private fun registerUnlockReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(unlockReceiver, filter, RECEIVER_EXPORTED)
        Log.d(TAG, "unlockReceiver registered")
    }

    private fun onUserUnlocked() {
        Log.d(TAG, "onUserUnlocked() called")

        if (isCapturing) {
            Log.d(TAG, "Already capturing, skipping...")
            return
        }

        if (checkCameraPermission()) {
            Log.d(TAG, "Camera permission granted, taking photo...")
            takeFrontCameraPhoto()
        } else {
            Log.w(TAG, "Camera permission not granted")
            showPermissionNotification()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun takeFrontCameraPhoto() {
        Log.d(TAG, "Initializing camera for photo capture...")
        isCapturing = true

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                Log.d(TAG, "CameraProvider obtained")

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                if (!cameraProvider!!.hasCamera(cameraSelector)) {
                    Log.e(TAG, "Front camera not available")
                    showErrorNotification("Front camera not available")
                    isCapturing = false
                    return@addListener
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetResolution(Size(1280, 720))
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .build()

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageCapture
                )

                Log.d(TAG, "Camera bound to lifecycle")

                Handler(Looper.getMainLooper()).postDelayed({
                    captureAndSavePhoto()
                }, 1000)

            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                showErrorNotification("Camera error: ${e.localizedMessage}")
                isCapturing = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndSavePhoto() {
        val imageCapture = this.imageCapture ?: run {
            Log.e(TAG, "ImageCapture is null, cannot capture photo")
            showErrorNotification("Camera not ready")
            isCapturing = false
            return
        }

        Log.d(TAG, "Starting photo capture...")

        val photoFile = try {
            createImageFile()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create image file", e)
            showErrorNotification("Failed to create file")
            isCapturing = false
            return
        }

        Log.d(TAG, "Photo will be saved to: ${photoFile.absolutePath}")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Photo saved successfully: ${photoFile.absolutePath}")

                    cameraExecutor.submit {
                        addPhotoToGallery(photoFile)
                    }

                    releaseCamera()
                    isCapturing = false
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    showErrorNotification("Capture failed: ${exception.localizedMessage}")
                    releaseCamera()
                    isCapturing = false
                }
            }
        )
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: filesDir

        return File(storageDir, "SECURITY_${timeStamp}.jpg").apply {
            parentFile?.mkdirs()
        }
    }

    private fun addPhotoToGallery(imageFile: File) {
        try {
            val photoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    imageFile
                )
            } else {
                Uri.fromFile(imageFile)
            }

            sendBroadcast(
                Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = photoUri
                }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MySecurity")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        imageFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    Log.d(TAG, "Photo added to gallery (Android Q+): $uri")
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetFile = File(picturesDir, "MySecurity/${imageFile.name}")

                picturesDir.mkdirs()
                imageFile.copyTo(targetFile, overwrite = true)

                Log.d(TAG, "Photo copied to: ${targetFile.absolutePath}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to add photo to gallery", e)
        }
    }

    private fun releaseCamera() {
        cameraProvider?.unbindAll()
        imageCapture = null
        cameraProvider = null
        Log.d(TAG, "Camera released")
    }

    private fun showErrorNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Security Camera Error")
            .setContentText(message)
            .setSmallIcon(R.drawable.icon_empty)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify((System.currentTimeMillis() + 1).toInt(), notification)
    }

    private fun showPermissionNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("REQUEST_CAMERA_PERMISSION", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Camera Permission Required")
            .setContentText("Tap to grant camera permission")
            .setSmallIcon(R.drawable.icon_empty)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify((System.currentTimeMillis() + 2).toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "MySecurity Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Security monitoring service"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setShowBadge(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            flags
        )

        Log.d(TAG, "Building foreground notification")
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("MySecurity Active")
            .setContentText("Monitoring device unlocks...")
            .setSmallIcon(R.drawable.icon_empty)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}