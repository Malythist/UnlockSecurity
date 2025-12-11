package com.malithyst.mysecurity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.malithyst.mysecurity.ui.theme.MySecurityTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("MySecurity", "POST_NOTIFICATIONS granted = $granted")
            // После ответа по нотификациям сразу переходим к камере
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
        enableEdgeToEdge()

        // Сначала уведомления, потом камера
        askNotificationPermissionIfNeeded()

        setContent {
            MySecurityTheme {
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                val initialState = if (MySecurityService.isRunning) {
                    ButtonState.Active
                } else {
                    ButtonState.Idle
                }

                var buttonState by remember { mutableStateOf(initialState) }

                val buttonText = when (buttonState) {
                    ButtonState.Idle -> "Включить"
                    ButtonState.Activating -> "Ожидание..."
                    ButtonState.Active -> "Выключить"
                }

                val buttonEnabled = buttonState != ButtonState.Activating

                MySecurityScreen(
                    text = buttonText,
                    enabled = buttonEnabled,
                    onClick = {
                        when (buttonState) {
                            ButtonState.Idle -> {
                                startSecurityService()
                                buttonState = ButtonState.Activating

                                Toast.makeText(
                                    context,
                                    "Пожалуйста, подождите",
                                    Toast.LENGTH_SHORT
                                ).show()

                                scope.launch {
                                    delay(1_000)
                                    buttonState =
                                        if (MySecurityService.isRunning) {
                                            ButtonState.Active
                                        } else {
                                            ButtonState.Idle
                                        }
                                }
                            }

                            ButtonState.Active -> {
                                stopSecurityService()
                                buttonState = ButtonState.Idle
                            }

                            ButtonState.Activating -> {
                                // ничего не делаем
                            }
                        }
                    }
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
                // если уже есть разрешение на нотификации — сразу идём за камерой
                askCameraPermissionIfNeeded()
            }
        } else {
            // на старых версиях нотификации не спрашиваются, сразу камера
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

private enum class ButtonState {
    Idle,
    Activating,
    Active
}

@Composable
fun MySecurityScreen(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF077E3C),
                contentColor = Color.White,
                disabledContainerColor = Color.LightGray,
                disabledContentColor = Color.Black)
        ) {
            Text(text)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MySecurityScreenPreview() {
    MySecurityTheme {
        MySecurityScreen(
            text = "Включить",
            enabled = true,
            onClick = {}
        )
    }
}