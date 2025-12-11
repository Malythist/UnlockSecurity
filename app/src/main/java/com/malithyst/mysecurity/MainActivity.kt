package com.malithyst.mysecurity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.malithyst.mysecurity.ui.theme.MySecurityTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.graphics.toColorInt
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("MySecurity", "POST_NOTIFICATIONS granted = $granted")
            askCameraPermissionIfNeeded()
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("MySecurity", "CAMERA granted = $granted")
            if (!granted) {
                Toast.makeText(
                    this,
                    "Без доступа к камере приложение не сможет делать снимки при разблокировке",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.White.toArgb()
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true

        askNotificationPermissionIfNeeded()

        setContent {
            MySecurityTheme {
                MySecurityNavHost(
                    startSecurityService = { startSecurityService() },
                    stopSecurityService = { stopSecurityService() }
                )
            }
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(this, permission) ==
                    PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(permission)
            } else {
                askCameraPermissionIfNeeded()
            }
        } else {
            askCameraPermissionIfNeeded()
        }
    }

    private fun askCameraPermissionIfNeeded() {
        val permission = Manifest.permission.CAMERA
        val granted = ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED

        if (!granted) {
            cameraPermissionLauncher.launch(permission)
        }
    }

    private fun startSecurityService() {
        Log.d("MySecurity", "startSecurityService() called")
        val intent = Intent(this, MySecurityService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopSecurityService() {
        Log.d("MySecurity", "stopSecurityService() called")
        val intent = Intent(this, MySecurityService::class.java)
        stopService(intent)
    }
}