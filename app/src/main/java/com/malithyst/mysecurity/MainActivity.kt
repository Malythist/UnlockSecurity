package com.malithyst.mysecurity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.malithyst.mysecurity.ui.theme.MySecurityTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("MySecurity", "POST_NOTIFICATIONS granted = $granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        askNotificationPermissionIfNeeded()

        setContent {
            MySecurityTheme {
                val scope = rememberCoroutineScope()

                // Стейт кнопки: idle -> activating -> active
                var buttonState by remember { mutableStateOf(ButtonState.Idle) }

                val buttonText = when (buttonState) {
                    ButtonState.Idle -> "activate"
                    ButtonState.Activating -> "please wait"
                    ButtonState.Active -> "disable"
                }

                val buttonEnabled = buttonState != ButtonState.Activating

                MySecurityScreen(
                    text = buttonText,
                    enabled = buttonEnabled,
                    onClick = {
                        when (buttonState) {
                            ButtonState.Idle -> {
                                // стартуем сервис
                                startSecurityService()
                                // переводим кнопку в режим "ждём"
                                buttonState = ButtonState.Activating

                                // через 7 секунд делаем кнопку "disable"
                                scope.launch {
                                    delay(7000)
                                    buttonState = ButtonState.Active
                                }
                            }

                            ButtonState.Active -> {
                                // стопаем сервис
                                stopSecurityService()
                                // возвращаем в начальное состояние
                                buttonState = ButtonState.Idle
                            }

                            ButtonState.Activating -> {
                                // игнорируем, кнопка и так disabled
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
            }
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
    Idle,        // "activate"
    Activating,  // "please wait" + disabled
    Active       // "disable"
}

@Composable
fun MySecurityScreen(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            enabled = enabled
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
            text = "activate",
            enabled = true,
            onClick = {}
        )
    }
}
