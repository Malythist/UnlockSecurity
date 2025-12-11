package com.malithyst.mysecurity

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object Routes {
    const val MAIN = "main"
    const val IMAGES = "images"
}

@Composable
fun MySecurityNavHost(
    startSecurityService: () -> Unit,
    stopSecurityService: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                onCreatedImagesClick = { navController.navigate(Routes.IMAGES) },
                onSettingsClick = { /* пока ничего */ },
                startSecurityService = startSecurityService,
                stopSecurityService = stopSecurityService
            )
        }

        composable(Routes.IMAGES) {
            ImagesScreen()
        }
    }
}

private enum class ButtonState {
    Idle,
    Activating,
    Active
}

@Composable
fun MainScreen(
    onCreatedImagesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    startSecurityService: () -> Unit,
    stopSecurityService: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val initialState = if (MySecurityService.isRunning) {
        ButtonState.Active
    } else {
        ButtonState.Idle
    }

    var buttonState by remember { mutableStateOf(initialState) }

    val buttonText = when (buttonState) {
        ButtonState.Idle -> "Включить защиту"
        ButtonState.Activating -> "Ожидание..."
        ButtonState.Active -> "Выключить"
    }

    val buttonEnabled = buttonState != ButtonState.Activating

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        ListItemWithArrow(
            title = "Созданные снимки",
            onClick = onCreatedImagesClick
        )

        Divider(color = Color.LightGray)

        ListItemWithArrow(
            title = "Настройки",
            onClick = onSettingsClick
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Button(
                onClick = {
                    when (buttonState) {
                        ButtonState.Idle -> {
                            startSecurityService()
                            buttonState = ButtonState.Activating

                            android.widget.Toast.makeText(
                                context,
                                "Пожалуйста, подождите",
                                android.widget.Toast.LENGTH_SHORT
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
                },
                enabled = buttonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF077E3C),
                    contentColor = Color.White,
                    disabledContainerColor = Color.LightGray,
                    disabledContentColor = Color.Black
                )
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun ListItemWithArrow(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.Black,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            Image(
                painter = painterResource(id = R.drawable.ic_arrow_right),
                contentDescription = "Открыть",
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
fun ImagesScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Здесь будут созданные снимки")
    }
}
